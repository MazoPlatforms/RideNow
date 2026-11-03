const root = document.documentElement;
root.classList.add("js");
const $ = (s, c = document) => c.querySelector(s);
const $$ = (s, c = document) => [...c.querySelectorAll(s)];
const calm = matchMedia("(prefers-reduced-motion: reduce)");
const fine = matchMedia("(hover: hover) and (pointer: fine)");

/* header: shadow after scroll */
const header = $(".hd");
const onScroll = () => header.classList.toggle("is-scrolled", scrollY > 12);
addEventListener("scroll", onScroll, { passive: true });
onScroll();

/* nav: dropdowns + mobile sheet */
const nav = $("#nav"), burger = $(".burger"), groups = $$("#nav details");
const mobile = matchMedia("(max-width: 1020px)");
function setMenu(open) {
  burger.setAttribute("aria-expanded", String(open));
  burger.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
  nav.classList.toggle("is-open", open);
  header.classList.toggle("menu-open", open && mobile.matches);
  document.body.style.overflow = open && mobile.matches ? "hidden" : "";
  groups.forEach((g) => (g.open = open && mobile.matches));
}
burger.addEventListener("click", () => setMenu(burger.getAttribute("aria-expanded") !== "true"));
groups.forEach((g) => g.addEventListener("toggle", () => {
  if (g.open && !mobile.matches) groups.forEach((o) => o !== g && (o.open = false));
}));
document.addEventListener("click", (e) => {
  if (!e.target.closest("#nav") && !mobile.matches) groups.forEach((g) => (g.open = false));
});
$$("#nav a").forEach((a) => a.addEventListener("click", () => setMenu(false)));
addEventListener("keydown", (e) => {
  if (e.key === "Escape") { groups.forEach((g) => (g.open = false)); setMenu(false); }
});
mobile.addEventListener("change", () => setMenu(false));

/* scroll reveal */
const rv = $$(".rv");
if ("IntersectionObserver" in window && !calm.matches) {
  const io = new IntersectionObserver((es) => es.forEach((e) => {
    if (e.isIntersecting) { e.target.classList.add("is-in"); io.unobserve(e.target); }
  }), { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
  rv.forEach((el) => io.observe(el));
} else rv.forEach((el) => el.classList.add("is-in"));

/* pause animations that are off-screen (keeps the page light) */
if ("IntersectionObserver" in window) {
  const pods = $$(".scene svg");
  const po = new IntersectionObserver((es) => es.forEach((e) => {
    e.target.classList.toggle("is-off", !e.isIntersecting);
    if (e.target.pauseAnimations) e.isIntersecting && !calm.matches ? e.target.unpauseAnimations() : e.target.pauseAnimations();
  }), { rootMargin: "80px" });
  $$(".scene svg, .tape, .kinetic-tape, .cash, .sec--sand, .stage, .bento").forEach((el) => po.observe(el));
  pods.forEach((s) => calm.matches && s.pauseAnimations?.());
}

/* hero parallax: pointer on desktop, scroll everywhere (transform only) */
const layers = $$(".scene .l");
let px = 0, py = 0, sy = 0, raf = 0;
const paint = () => {
  raf = 0;
  layers.forEach((l) => {
    const d = +l.dataset.depth || 0;
    l.style.transform = `translate3d(${px * d * -1}px,${(py * d * -0.5) + sy * d * -0.012}px,0)`;
  });
};
const queue = () => { if (!raf) raf = requestAnimationFrame(paint); };
if (!calm.matches) {
  const hero = $(".hero");
  addEventListener("scroll", () => { if (scrollY < innerHeight * 1.2) { sy = scrollY; queue(); } }, { passive: true });
  if (fine.matches) hero.addEventListener("pointermove", (e) => {
    px = (e.clientX / innerWidth - 0.5) * 1.1;
    py = (e.clientY / innerHeight - 0.5) * 1.1;
    queue();
  });
}

/* concept phone: tabs + tilt */
const tabs = $$("[data-tab]"), panels = $$("[data-panel]"), sheets = $$("[data-sheet]");
function pick(tab, focus) {
  const id = tab.dataset.tab;
  tabs.forEach((t) => { const on = t === tab; t.setAttribute("aria-selected", on); t.tabIndex = on ? 0 : -1; });
  panels.forEach((p) => (p.hidden = p.dataset.panel !== id));
  sheets.forEach((s) => (s.hidden = s.dataset.sheet !== id));
  if (focus) tab.focus();
}
tabs.forEach((t, i) => {
  t.addEventListener("click", () => pick(t));
  t.addEventListener("keydown", (e) => {
    const k = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    if (k) { e.preventDefault(); pick(tabs[(i + k + tabs.length) % tabs.length], true); }
    if (e.key === "Home") { e.preventDefault(); pick(tabs[0], true); }
    if (e.key === "End") { e.preventDefault(); pick(tabs.at(-1), true); }
  });
});
const stage = $(".stage"), phone = $(".phone");
if (stage && fine.matches && !calm.matches) {
  stage.addEventListener("pointermove", (e) => {
    const r = stage.getBoundingClientRect();
    phone.style.setProperty("--ry", `${((e.clientX - r.left) / r.width - 0.5) * 22}deg`);
    phone.style.setProperty("--rx", `${((e.clientY - r.top) / r.height - 0.5) * -14}deg`);
  });
  stage.addEventListener("pointerleave", () => { phone.style.removeProperty("--ry"); phone.style.removeProperty("--rx"); });
}

/* preview dialog (no store listings exist yet) */
const dlg = $("#preview"), msg = $("#preview-msg");
let last = null;
$$("[data-store]").forEach((b) => b.addEventListener("click", () => {
  if (!(dlg instanceof HTMLDialogElement)) return;
  last = b;
  msg.textContent = b.dataset.store === "Apple App Store"
    ? "There is no RideNow Rider iOS app or App Store listing today. This button is a preview state only."
    : "There is no public RideNow Rider Google Play listing today. This button is a preview state only.";
  dlg.showModal();
  $("[data-close]", dlg).focus();
}));
$$("[data-close]", dlg).forEach((b) => b.addEventListener("click", () => dlg.close()));
dlg.addEventListener("click", (e) => { if (e.target === dlg) dlg.close(); });
dlg.addEventListener("close", () => last?.focus());

$$("[data-year]").forEach((n) => (n.textContent = new Date().getFullYear()));
