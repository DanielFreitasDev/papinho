import { afterEach, describe, expect, it } from "vitest"

import {
  applyDisplayPrefs,
  parseDisplayPrefs,
  setDisplayPrefs,
} from "./display-prefs"

describe("preferências de exibição (UI-02)", () => {
  afterEach(() => {
    localStorage.clear()
    setDisplayPrefs({
      palette: "papinho",
      wallpaper: "none",
      doodles: true,
      wallpaperDim: 0,
      fontSize: "normal",
      density: "comfortable",
    })
  })

  it("aplica no <html> e guarda neste aparelho", () => {
    setDisplayPrefs({ wallpaper: "dots", fontSize: "large" })
    const root = document.documentElement
    expect(root.dataset.wallpaper).toBe("dots")
    expect(root.dataset.fontSize).toBe("large")
    expect(root.dataset.density).toBe("comfortable")
    expect(JSON.parse(localStorage.getItem("papinho.display")!)).toMatchObject({
      wallpaper: "dots",
      fontSize: "large",
    })
  })

  it("reaplica o estado atual sem argumentos", () => {
    setDisplayPrefs({ density: "compact" })
    document.documentElement.dataset.density = "outra"
    applyDisplayPrefs()
    expect(document.documentElement.dataset.density).toBe("compact")
  })

  it("troca a paleta de cor do app inteiro pelo atributo do <html>", () => {
    expect(document.documentElement.dataset.palette).toBe("papinho")
    setDisplayPrefs({ palette: "oceano" })
    expect(document.documentElement.dataset.palette).toBe("oceano")
    expect(JSON.parse(localStorage.getItem("papinho.display")!).palette).toBe(
      "oceano"
    )
  })

  it("volta ao padrão com valores desconhecidos ou guardados quebrados", () => {
    expect(parseDisplayPrefs(null).palette).toBe("papinho")
    expect(
      parseDisplayPrefs('{"palette":"neon","fontSize":"large"}')
    ).toMatchObject({
      palette: "papinho",
      fontSize: "large",
    })
    expect(parseDisplayPrefs("{quebrado").palette).toBe("papinho")
  })

  it("papel de parede com desenhos por padrão e escurecimento pelo CSSOM", () => {
    const root = document.documentElement
    expect(root.dataset.doodles).toBe("on")
    setDisplayPrefs({ wallpaper: "color-menta", doodles: false })
    expect(root.dataset.wallpaper).toBe("color-menta")
    expect(root.dataset.doodles).toBe("off")
    setDisplayPrefs({ wallpaper: "image", wallpaperDim: 30 })
    expect(root.style.getPropertyValue("--wallpaper-dim")).toBe("0.3")
    expect(
      parseDisplayPrefs('{"wallpaperDim":35,"doodles":"sim"}')
    ).toMatchObject({
      wallpaperDim: 0,
      doodles: true,
    })
  })
})
