const documentRoot = document.documentElement;
documentRoot.classList.add("js");

const storyTabs = [...document.querySelectorAll("[data-story-tab]")];
const storyPanels = [...document.querySelectorAll("[data-story-panel]")];
const phoneContents = [...document.querySelectorAll("[data-phone-content]")];

function selectStoryTab(selectedTab, { focus = false } = {}) {
  const selectedMoment = selectedTab.dataset.storyTab;
  storyTabs.forEach((tab) => {
    const selected = tab === selectedTab;
    tab.classList.toggle("is-active", selected);
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  storyPanels.forEach((panel) => { panel.hidden = panel.dataset.storyPanel !== selectedMoment; });
  phoneContents.forEach((content) => { content.hidden = content.dataset.phoneContent !== selectedMoment; });
  if (focus) selectedTab.focus();
}

storyTabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectStoryTab(tab));
  tab.addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const last = storyTabs.length - 1;
    const nextIndex = event.key === "ArrowRight" ? (index + 1) % storyTabs.length
      : event.key === "ArrowLeft" ? (index - 1 + storyTabs.length) % storyTabs.length
        : event.key === "End" ? last : 0;
    selectStoryTab(storyTabs[nextIndex], { focus: true });
  });
});

const previewDialog = document.querySelector("[data-preview-dialog]");
const previewMessage = document.querySelector("[data-preview-message]");
const dismissDialog = document.querySelector("[data-dialog-dismiss]");
let lastPreviewTrigger = null;

document.querySelectorAll("[data-store]").forEach((button) => {
  button.addEventListener("click", () => {
    if (!(previewDialog instanceof HTMLDialogElement)) return;
    lastPreviewTrigger = button;
    const store = button.dataset.store;
    const page = document.body.dataset.page;
    if (previewMessage) {
      const appName = page === "driver" ? "RideNow Driver" : "RideNow Rider";
      previewMessage.textContent = store === "Apple App Store"
        ? `There is no ${appName} iOS app or App Store listing today. This button is a preview state only.`
        : `There is no public ${appName} Google Play listing today. This button is a preview state only.`;
    }
    previewDialog.showModal();
    dismissDialog?.focus();
  });
});
dismissDialog?.addEventListener("click", () => previewDialog?.close());
previewDialog?.addEventListener("close", () => lastPreviewTrigger?.focus());

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const saveData = navigator.connection?.saveData === true;
const revealItems = document.querySelectorAll("[data-reveal]");
if ("IntersectionObserver" in window && !reduceMotion.matches) {
  const revealObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -28px 0px" });
  revealItems.forEach((item) => revealObserver.observe(item));
} else {
  revealItems.forEach((item) => item.classList.add("is-visible"));
}

const motionVideo = document.querySelector("[data-motion-video]");
let motionObserver;
let motionTimer;
let videoRequested = false;

function requestHeroMotion() {
  if (videoRequested || !motionVideo || reduceMotion.matches || saveData) return;
  const source = motionVideo.querySelector("source[data-src]");
  if (!source) return;
  videoRequested = true;
  source.src = source.dataset.src;
  source.removeAttribute("data-src");
  motionVideo.load();
  motionVideo.addEventListener("canplay", async () => {
    try {
      await motionVideo.play();
      motionVideo.classList.add("is-playing");
    } catch {
      // The still poster remains the complete visual when autoplay is unavailable.
    }
  }, { once: true });
}

if (motionVideo && !reduceMotion.matches && !saveData && "IntersectionObserver" in window) {
  motionObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      motionObserver.disconnect();
      const schedule = window.requestIdleCallback ?? ((callback) => window.setTimeout(callback, 1400));
      schedule(requestHeroMotion, { timeout: 2500 });
    }
  }, { rootMargin: "120px" });
  motionObserver.observe(motionVideo);
}

document.addEventListener("visibilitychange", () => {
  if (!motionVideo || motionVideo.readyState === 0) return;
  if (document.hidden || reduceMotion.matches) motionVideo.pause();
  else if (motionVideo.classList.contains("is-playing")) motionVideo.play().catch(() => {});
});
reduceMotion.addEventListener?.("change", (event) => {
  if (event.matches && motionVideo) {
    motionVideo.pause();
    motionVideo.classList.remove("is-playing");
  }
});

const parallaxImages = [...document.querySelectorAll("[data-parallax]")];
let parallaxFrame = 0;
reduceMotion.addEventListener?.("change", (event) => {
  if (event.matches) parallaxImages.forEach((image) => image.style.setProperty("--parallax-y", "0px"));
});
if (parallaxImages.length && !reduceMotion.matches) {
  const updateParallax = () => {
    const viewportHeight = window.innerHeight || 1;
    parallaxImages.forEach((image) => {
      const bounds = image.parentElement.getBoundingClientRect();
      if (bounds.bottom < 0 || bounds.top > viewportHeight) return;
      const progress = (viewportHeight - bounds.top) / (viewportHeight + bounds.height);
      image.style.setProperty("--parallax-y", `${(progress - 0.5) * -24}px`);
    });
    parallaxFrame = 0;
  };
  window.addEventListener("scroll", () => {
    if (!parallaxFrame) parallaxFrame = window.requestAnimationFrame(updateParallax);
  }, { passive: true });
}

document.querySelectorAll("[data-year]").forEach((node) => { node.textContent = String(new Date().getFullYear()); });
