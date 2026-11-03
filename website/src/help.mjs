const input = document.getElementById("help-search");
const qs = [...document.querySelectorAll("[data-q]")];
const groups = [...document.querySelectorAll("[data-group]")];
const status = document.getElementById("help-status");
const none = document.getElementById("help-none");
function filter() {
  const v = input.value.trim().toLowerCase();
  let n = 0;
  qs.forEach((d) => {
    const hit = !v || d.textContent.toLowerCase().includes(v);
    d.hidden = !hit;
    if (hit) n++;
    if (v && hit) d.open = true; else if (!v) d.open = false;
  });
  groups.forEach((g) => (g.hidden = !g.querySelector("[data-q]:not([hidden])")));
  none.hidden = n > 0;
  status.textContent = v ? `${n} answer${n === 1 ? "" : "s"} found.` : "Browse the answers below.";
}
input.addEventListener("input", filter);
addEventListener("keydown", (e) => {
  if (e.key === "/" && !/input|textarea/i.test(document.activeElement.tagName)) { e.preventDefault(); input.focus(); }
});
