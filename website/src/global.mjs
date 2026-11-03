const menuToggle = document.querySelector("[data-menu-toggle]");
const siteNav = document.querySelector("[data-site-nav]");

function setMenuOpen(open) {
  if (!menuToggle || !siteNav) return;
  menuToggle.setAttribute("aria-expanded", String(open));
  menuToggle.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
  siteNav.classList.toggle("is-open", open);
  if (!open) siteNav.querySelectorAll("details[open]").forEach((group) => { group.open = false; });
}

menuToggle?.addEventListener("click", () => setMenuOpen(menuToggle.getAttribute("aria-expanded") !== "true"));
siteNav?.querySelectorAll("a").forEach((link) => link.addEventListener("click", () => setMenuOpen(false)));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    setMenuOpen(false);
    siteNav?.querySelectorAll("details[open]").forEach((group) => { group.open = false; });
  }
});
document.addEventListener("click", (event) => {
  if (siteNav?.classList.contains("is-open") && !siteNav.contains(event.target) && !menuToggle?.contains(event.target)) setMenuOpen(false);
  if (!event.target.closest(".nav-group")) document.querySelectorAll(".nav-group[open]").forEach((group) => { group.open = false; });
});

const helpSearch = document.querySelector("[data-help-search]");
const helpItems = [...document.querySelectorAll("[data-help-item]")];
const helpStatus = document.querySelector("[data-help-status]");
helpSearch?.addEventListener("input", () => {
  const query = helpSearch.value.trim().toLocaleLowerCase();
  let visible = 0;
  helpItems.forEach((item) => {
    const matches = !query || item.dataset.searchText.toLocaleLowerCase().includes(query);
    item.hidden = !matches;
    if (matches) visible += 1;
  });
  document.querySelectorAll(".help-group").forEach((group) => {
    group.hidden = !group.querySelector("[data-help-item]:not([hidden])");
  });
  if (helpStatus) helpStatus.textContent = query ? `${visible} ${visible === 1 ? "answer" : "answers"} found.` : "Browse the answers below.";
});
document.addEventListener("keydown", (event) => {
  if (event.key === "/" && !["INPUT", "TEXTAREA", "SELECT"].includes(document.activeElement?.tagName)) {
    if (helpSearch) { event.preventDefault(); helpSearch.focus(); }
  }
});

const walkthroughTabs = [...document.querySelectorAll("[data-walkthrough-tab]")];
const walkthroughPanels = [...document.querySelectorAll("[data-walkthrough-panel]")];
function selectWalkthrough(tab, focus = false) {
  const selected = tab.dataset.walkthroughTab;
  walkthroughTabs.forEach((item) => {
    const active = item === tab;
    item.setAttribute("aria-selected", String(active));
    item.tabIndex = active ? 0 : -1;
    item.classList.toggle("is-active", active);
  });
  walkthroughPanels.forEach((panel) => { panel.hidden = panel.dataset.walkthroughPanel !== selected; });
  document.querySelector(".walkthrough__stage")?.style.setProperty("--step", selected);
  if (focus) tab.focus();
}
walkthroughTabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectWalkthrough(tab));
  tab.addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === "ArrowRight" ? (index + 1) % walkthroughTabs.length
      : event.key === "ArrowLeft" ? (index - 1 + walkthroughTabs.length) % walkthroughTabs.length
        : event.key === "End" ? walkthroughTabs.length - 1 : 0;
    selectWalkthrough(walkthroughTabs[next], true);
  });
});

const contactForm = document.querySelector("[data-contact-form]");
const contactStatus = document.querySelector("[data-contact-status]");
contactForm?.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!contactForm.reportValidity()) return;
  if (contactStatus) contactStatus.textContent = "Your fields are valid, but this preview cannot send or save your message. Nothing was transmitted.";
});

const previewDialog = document.querySelector("[data-preview-dialog]");
const previewMessage = document.querySelector("[data-preview-message]");
const dismissDialog = document.querySelector("[data-dialog-dismiss]");
let lastPreviewTrigger = null;
if (!document.body.dataset.page) {
  document.querySelectorAll("[data-store]").forEach((button) => {
    button.addEventListener("click", () => {
      if (!(previewDialog instanceof HTMLDialogElement)) return;
      lastPreviewTrigger = button;
      const store = button.dataset.store;
      if (previewMessage) previewMessage.textContent = store === "Apple App Store"
        ? "There is no RideNow iOS app or App Store listing. This control is a preview only."
        : "There is no public RideNow Google Play listing. This control is a preview only.";
      previewDialog.showModal();
      dismissDialog?.focus();
    });
  });
}
dismissDialog?.addEventListener("click", () => previewDialog?.close());
previewDialog?.addEventListener("close", () => lastPreviewTrigger?.focus());
document.querySelectorAll("[data-year]").forEach((node) => { node.textContent = String(new Date().getFullYear()); });
