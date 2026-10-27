import { describe, expect, it } from "vitest"

import { enterSends, parseChatPrefs, replaceEmoticon } from "./chat-prefs"

describe("preferências de digitação e envio", () => {
  it("começa como o WhatsApp e ignora valores estranhos", () => {
    expect(parseChatPrefs(null)).toEqual({
      enterToSend: "auto",
      spellcheck: true,
      emojiReplace: true,
      sendSound: false,
    })
    expect(
      parseChatPrefs('{"enterToSend":"sempre","sendSound":true,"spellcheck":0}')
    ).toMatchObject({ enterToSend: "auto", sendSound: true, spellcheck: true })
  })

  it("Enter envia no automático só com mouse; ligado ou desligado vale sempre", () => {
    const prefs = parseChatPrefs(null)
    expect(enterSends(prefs, false)).toBe(true)
    expect(enterSends(prefs, true)).toBe(false)
    expect(enterSends({ ...prefs, enterToSend: "on" }, true)).toBe(true)
    expect(enterSends({ ...prefs, enterToSend: "off" }, false)).toBe(false)
  })

  it("troca a carinha antes do espaço digitado e mantém o cursor depois dele", () => {
    expect(replaceEmoticon("oi :) ", 6)).toEqual({ text: "oi 🙂 ", caret: 6 })
    expect(replaceEmoticon("<3 ", 3)).toEqual({ text: "❤️ ", caret: 3 })
    // No meio do texto: só o que está antes do cursor muda.
    expect(replaceEmoticon("a :D b", 5)).toEqual({ text: "a 😃 b", caret: 5 })
  })

  it("não mexe em URLs, palavras com dois-pontos nem sem o espaço", () => {
    expect(replaceEmoticon("veja http:// ", 13)).toBeNull()
    expect(replaceEmoticon("a:) ", 4)).toBeNull()
    expect(replaceEmoticon("oi :)", 5)).toBeNull()
    expect(replaceEmoticon("hora 10:30 ", 11)).toBeNull()
  })
})
