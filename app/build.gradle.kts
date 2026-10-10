import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val generatedOcrAssets = layout.buildDirectory.dir("generated/ocr-assets")
val tessdataRevision = "87416418657359cb625c412a48b6e1d6d41c29bd"
val tessdataModels = mapOf(
    "eng.traineddata" to "bbef4675053b5b468cdb477053e28b1c698ba08e",
    "hin.traineddata" to "a8f0aaec09378115b12a2c6287c57695d082aea2",
    "tel.traineddata" to "ee8a33b0f658fc75b8cbfe8c10413eac4f41e6ad"
)

android {
    namespace = "com.kaagazvault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kaagazvault"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("main").assets.srcDir(generatedOcrAssets.get().asFile)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val prepareOcrModels by tasks.registering {
    val modelOutputs = tessdataModels.keys.map { name -> generatedOcrAssets.map { it.file("tessdata/$name") } }
    outputs.files(modelOutputs)
    // Re-hash cached files on every build; Gradle's existence-only up-to-date check is insufficient here.
    outputs.upToDateWhen { false }

    doLast {
        val outputDirectory = generatedOcrAssets.get().file("tessdata").asFile
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw GradleException("Could not create generated OCR model directory")
        }

        tessdataModels.forEach { (name, expectedGitBlobSha) ->
            val destination = File(outputDirectory, name)
            val validExisting = destination.isFile && run {
                val bytes = destination.readBytes()
                val digest = MessageDigest.getInstance("SHA-1")
                digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
                digest.update(bytes)
                digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) } == expectedGitBlobSha
            }
            if (validExisting) return@forEach

            val temporary = File(outputDirectory, "$name.download")
            val url = URL(
                "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$tessdataRevision/$name"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            try {
                connection.instanceFollowRedirects = true
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }

            val bytes = temporary.readBytes()
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
            val actualGitBlobSha = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            if (actualGitBlobSha != expectedGitBlobSha) {
                temporary.delete()
                throw GradleException("Pinned OCR model integrity check failed for $name")
            }
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                throw GradleException("Could not replace invalid OCR model $name")
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                throw GradleException("Could not install OCR model $name")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareOcrModels)
}

dependencies {
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
    implementation(libs.pdfium.android)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
