(function () {
  "use strict";

  var script = document.currentScript;
  var token = script && (script.dataset.token || script.dataset.cacanodeToken);
  if (!script || !token) {
    console.error("CacaNode widget requires a data-token attribute.");
    return;
  }

  var sourceUrl = new URL(script.src, window.location.href);
  var frame = document.createElement("iframe");
  frame.src = sourceUrl.origin + "/widget/widget.html";
  frame.title = "Customer support chat";
  frame.setAttribute("aria-label", "Customer support chat");
  frame.setAttribute("allow", "clipboard-write");
  frame.style.position = "fixed";
  frame.style.right = "20px";
  frame.style.bottom = "20px";
  frame.style.width = "72px";
  frame.style.height = "72px";
  frame.style.border = "0";
  frame.style.background = "transparent";
  frame.style.zIndex = "2147483000";
  frame.style.colorScheme = "light";
  frame.style.transition = "width 160ms ease, height 160ms ease";

  window.addEventListener("message", function (event) {
    if (event.source !== frame.contentWindow || event.origin !== sourceUrl.origin) return;
    if (!event.data || event.data.source !== "cacanode-widget") return;
    if (event.data.type === "ready") {
      frame.contentWindow.postMessage({ source: "cacanode-host", type: "init", token: token }, sourceUrl.origin);
    }
    if (event.data.type === "resize") {
      frame.style.width = event.data.open ? "min(390px, calc(100vw - 24px))" : "72px";
      frame.style.height = event.data.open ? "min(640px, calc(100vh - 24px))" : "72px";
    }
    if (event.data.type === "position") {
      frame.style.left = event.data.position === "BOTTOM_LEFT" ? "20px" : "auto";
      frame.style.right = event.data.position === "BOTTOM_LEFT" ? "auto" : "20px";
    }
  });

  document.body.appendChild(frame);
})();
