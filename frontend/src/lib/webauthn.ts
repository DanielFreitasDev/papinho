import { apiFetch } from "@/lib/api-client"

/**
 * Cerimônias de passkey contra os endpoints do Spring Security (ID-04), com a API JSON do WebAuthn nível 3
 * (`parseCreationOptionsFromJSON`, `toJSON`), presente nos navegadores atuais.
 */

type JsonPublicKeyCredential = {
  toJSON(): unknown
}

export function passkeysSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    "PublicKeyCredential" in window &&
    typeof PublicKeyCredential.parseCreationOptionsFromJSON === "function" &&
    typeof PublicKeyCredential.parseRequestOptionsFromJSON === "function"
  )
}

/** Registra uma passkey para o usuário da sessão. */
export async function registerPasskey(label: string): Promise<void> {
  const options = await apiFetch<PublicKeyCredentialCreationOptionsJSON>(
    "/webauthn/register/options",
    { method: "POST" }
  )
  const credential = (await navigator.credentials.create({
    publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(options),
  })) as (PublicKeyCredential & JsonPublicKeyCredential) | null
  if (!credential) {
    throw new Error("passkey-cancelled")
  }
  await apiFetch<unknown>("/webauthn/register", {
    method: "POST",
    json: { publicKey: { credential: credential.toJSON(), label } },
  })
}

/** Entra com uma passkey; o servidor abre a sessão em caso de sucesso. */
export async function loginWithPasskey(): Promise<void> {
  const options = await apiFetch<PublicKeyCredentialRequestOptionsJSON>(
    "/webauthn/authenticate/options",
    { method: "POST" }
  )
  const credential = (await navigator.credentials.get({
    publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(options),
  })) as (PublicKeyCredential & JsonPublicKeyCredential) | null
  if (!credential) {
    throw new Error("passkey-cancelled")
  }
  await apiFetch<unknown>("/login/webauthn", {
    method: "POST",
    json: credential.toJSON(),
  })
}

export async function deletePasskey(id: string): Promise<void> {
  await apiFetch<void>(`/webauthn/register/${encodeURIComponent(id)}`, {
    method: "DELETE",
  })
}
