import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { PhotoCropper } from "./photo-cropper"

const drawImage = vi.fn()

beforeEach(() => {
  drawImage.mockClear()
  // Imagem 2:1: no zoom 1, sobram 256 px de cada lado na horizontal e nada na vertical.
  vi.stubGlobal(
    "createImageBitmap",
    vi.fn(() => Promise.resolve({ width: 1024, height: 512 }))
  )
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
    fillRect: vi.fn(),
    drawImage,
  } as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function lastX() {
  return drawImage.mock.calls.at(-1)?.[1] as number
}

describe("PhotoCropper", () => {
  it("para o deslocamento na borda: voltar mexe a imagem no primeiro toque", async () => {
    render(
      <PhotoCropper file={new File(["x"], "foto.png")} onChange={() => {}} />
    )
    const canvas = screen.getByRole("img")
    await waitFor(() => expect(drawImage).toHaveBeenCalled())
    expect(lastX()).toBe(-256)

    for (let i = 0; i < 40; i++) {
      fireEvent.keyDown(canvas, { key: "ArrowLeft" })
    }
    await waitFor(() => expect(lastX()).toBe(0))
    fireEvent.keyDown(canvas, { key: "ArrowRight" })
    await waitFor(() => expect(lastX()).toBe(-16))
  })

  it("ao diminuir o zoom, o deslocamento acompanha a nova borda", async () => {
    render(
      <PhotoCropper file={new File(["x"], "foto.png")} onChange={() => {}} />
    )
    const canvas = screen.getByRole("img")
    const slider = screen.getByRole("slider", { hidden: true })
    await waitFor(() => expect(drawImage).toHaveBeenCalled())

    // Zoom 3: sobram 1280 px de cada lado.
    fireEvent.keyDown(slider, { key: "End" })
    await waitFor(() => expect(drawImage.mock.calls.at(-1)?.[3]).toBe(3072))
    for (let i = 0; i < 100; i++) {
      fireEvent.keyDown(canvas, { key: "ArrowLeft" })
    }
    await waitFor(() => expect(lastX()).toBe(0))
    fireEvent.keyDown(slider, { key: "Home" })
    await waitFor(() => expect(drawImage.mock.calls.at(-1)?.[3]).toBe(1024))
    fireEvent.keyDown(canvas, { key: "ArrowRight" })
    await waitFor(() => expect(lastX()).toBe(-16))
  })
})
