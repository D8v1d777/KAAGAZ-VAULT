import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  reporter: "list",
  timeout: 30_000,
  use: {
    browserName: "chromium",
    headless: true,
    viewport: { width: 1365, height: 900 },
    trace: "retain-on-failure"
  }
});
