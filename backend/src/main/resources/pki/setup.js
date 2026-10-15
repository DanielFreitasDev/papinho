// Destaca as instruções do sistema e do navegador detectados. Sem JavaScript, todas continuam visíveis.
(function () {
  "use strict"
  var ua = navigator.userAgent || ""
  var platform = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || ""
  var os = null
  if (/iPhone|iPad|iPod/i.test(ua) || (/Mac/i.test(platform) && navigator.maxTouchPoints > 1)) os = "ios"
  else if (/Android/i.test(ua)) os = "android"
  else if (/Windows/i.test(ua) || /Win/i.test(platform)) os = "windows"
  else if (/Mac/i.test(ua) || /Mac/i.test(platform)) os = "macos"
  else if (/Linux|X11|CrOS/i.test(ua)) os = "linux"
  var firefox = /Firefox\//i.test(ua) && !/Seamonkey/i.test(ua)

  var names = { ios: "iPhone/iPad", android: "Android", windows: "Windows", macos: "macOS", linux: "Linux" }
  var detected = document.getElementById("detected")
  var parts = []
  if (os) {
    var section = document.querySelector('[data-os="' + os + '"]')
    if (section) {
      section.open = true
      section.classList.add("recommended")
      section.parentNode.insertBefore(section, detected.nextSibling)
    }
    parts.push(names[os])
  }
  if (firefox) {
    var ff = document.querySelector('[data-browser="firefox"]')
    if (ff) {
      ff.open = true
      ff.classList.add("recommended")
    }
    parts.push("Firefox")
  }
  if (parts.length && detected) {
    detected.textContent = "Detectamos: " + parts.join(" com ") + ". As instruções indicadas já estão abertas."
    detected.hidden = false
  }
})()
