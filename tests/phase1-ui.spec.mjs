import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const pageUrl = "file://" + path.resolve(here, "../index.html");

test.beforeEach(async ({ page }) => {
  await page.goto(pageUrl);
});

test("shows illustrative sample documents and prototype status", async ({ page }) => {
  await expect(page.getByRole("heading", { name: "Everything important, in one place." })).toBeVisible();
  await expect(page.locator(".card")).toHaveCount(6);
  await expect(page.getByText("Prototype preview: sample records are illustrative.")).toBeVisible();
  await expect(page.getByText("Not connected", { exact: true })).toBeVisible();
});

test("search filters documents by name", async ({ page }) => {
  await page.getByRole("searchbox", { name: "Search documents" }).fill("passport");
  await expect(page.locator(".card")).toHaveCount(1);
  await expect(page.locator(".card h3")).toHaveText("Passport");
});

test("category filters show matching documents", async ({ page }) => {
  await page.getByRole("button", { name: "Filter by Health" }).click();
  await expect(page.locator(".card")).toHaveCount(2);
  await expect(page.locator(".heading h2")).toHaveText("Health");
});

test("favourites update the list and the count", async ({ page }) => {
  await expect(page.locator("#starTotal")).toHaveText("2");
  await page.getByRole("button", { name: "Remove from favourites: Passport" }).click();
  await expect(page.locator("#starTotal")).toHaveText("1");
  await page.locator('[data-view="starred"]').click();
  await expect(page.locator(".card")).toHaveCount(1);
  await expect(page.locator(".card h3")).toHaveText("Health insurance");
});

test("sort toggle changes document order", async ({ page }) => {
  await page.locator("#sort").click();
  await expect(page.locator(".card h3").first()).toHaveText("Bank account details");
});

test("grid and list controls update the layout state", async ({ page }) => {
  await page.getByRole("button", { name: "List view" }).click();
  await expect(page.locator("#listView")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#docs")).toHaveClass(/list/);
  await page.getByRole("button", { name: "Grid view" }).click();
  await expect(page.locator("#gridView")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#docs")).not.toHaveClass(/list/);
});

test("detail dialog favourite action updates in place", async ({ page }) => {
  await page.getByRole("button", { name: "View details" }).first().click();
  const dialog = page.getByRole("dialog", { name: "Passport" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: /In favourites/ }).click();
  await expect(dialog.getByRole("button", { name: /Add to favourites/ })).toBeVisible();
  await expect(page.locator("#starTotal")).toHaveText("1");
});

test("file selection adds only preview metadata and rejects unsupported types", async ({ page }) => {
  await page.locator("#files").setInputFiles({
    name: "meeting-notes.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("prototype fixture")
  });
  await expect(page.locator(".card h3", { hasText: "meeting-notes" })).toBeVisible();
  await expect(page.getByRole("status")).toContainText("preview only");
  await page.locator("#files").setInputFiles({
    name: "unexpected.exe",
    mimeType: "application/octet-stream",
    buffer: Buffer.from("not a document")
  });
  await expect(page.getByRole("status")).toContainText("Unsupported file");
  await expect(page.locator(".card h3", { hasText: "unexpected" })).toHaveCount(0);
});

test("slash shortcut focuses search and mobile layout avoids horizontal overflow", async ({ page }) => {
  await page.keyboard.press("/");
  await expect(page.getByRole("searchbox", { name: "Search documents" })).toBeFocused();
  await page.setViewportSize({ width: 390, height: 844 });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(overflow).toBe(false);
  await expect(page.getByRole("button", { name: "Filter by Health" })).toBeAttached();
});

test("axe accessibility scan reports no WCAG A/AA violations on desktop or mobile", async ({ page }) => {
  const tags = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];
  const desktop = await new AxeBuilder({ page }).withTags(tags).analyze();
  expect(desktop.violations, "Desktop violations: " + JSON.stringify(desktop.violations, null, 2)).toEqual([]);
  await page.setViewportSize({ width: 390, height: 844 });
  const mobile = await new AxeBuilder({ page }).withTags(tags).analyze();
  expect(mobile.violations, "Mobile violations: " + JSON.stringify(mobile.violations, null, 2)).toEqual([]);
});
