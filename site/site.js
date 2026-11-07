// Install-snippet tabs + a small in-browser pluralization sketch.
(function () {
  // ---- tabs ----
  document.querySelectorAll("[data-tabs]").forEach(function (group) {
    var buttons = group.querySelectorAll(".tab-btn");
    var panels = group.querySelectorAll(".tab-panel");
    buttons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        buttons.forEach(function (b) { b.classList.remove("active"); });
        panels.forEach(function (p) { p.classList.remove("active"); });
        btn.classList.add("active");
        var panel = group.querySelector('#' + btn.getAttribute("data-tab"));
        if (panel) panel.classList.add("active");
      });
    });
  });

  // ---- demo: browser-side approximation of the three productive rules ----
  // The real engine is the Java library (157k-entry lexicon + compounds +
  // casing policy). This sketch covers a few irregulars + the 3 rules only.
  var IRREGULAR = {
    child: "children", person: "people", mouse: "mice", die: "dice",
    man: "men", woman: "women", tooth: "teeth", foot: "feet", goose: "geese",
    ox: "oxen", analysis: "analyses", cactus: "cactuses", quiz: "quizzes",
    mother: "mothers", passer: "passers", city: "cities", blouse: "blouses",
    scarf: "scarves", life: "lives", knife: "knives"
  };

  function pluralize(word) {
    if (!word) return "";
    var lower = word.toLowerCase();
    // compound head demo: mother-in-law -> mothers-in-law
    var inl = lower.indexOf("-in-law");
    if (inl !== -1) {
      return pluralize(word.slice(0, inl)) + word.slice(inl);
    }
    if (IRREGULAR[lower]) {
      var p = IRREGULAR[lower];
      if (word === word.toUpperCase()) return p.toUpperCase();
      if (word[0] === word[0].toUpperCase()) return p[0].toUpperCase() + p.slice(1);
      return p;
    }
    var out;
    if (/([^aeiou]y)$/.test(lower)) out = lower.slice(0, -1) + "ies";
    else if (/(s|x|z|ch|sh)$/.test(lower)) out = lower + "es";
    else out = lower + "s";
    if (word === word.toUpperCase()) return out.toUpperCase();
    if (word[0] === word[0].toUpperCase()) return out[0].toUpperCase() + out.slice(1);
    return out;
  }

  var input = document.getElementById("demo-input");
  var out = document.getElementById("demo-output");
  var btns = document.querySelectorAll("[data-demo]");
  function run() {
    if (!input || !out) return;
    var w = input.value.trim();
    out.textContent = w ? pluralize(w) : "—";
  }
  if (input) {
    input.addEventListener("input", run);
    input.addEventListener("keydown", function (e) { if (e.key === "Enter") run(); });
  }
  btns.forEach(function (b) {
    b.addEventListener("click", function () {
      if (input) { input.value = b.getAttribute("data-demo"); run(); }
    });
  });
  run();
})();
