import { z } from "@/lib/zod"

/** Mesmas regras do backend (Username.java): normaliza e valida o @usuario. */
export function normalizeUsername(raw: string): string {
  return raw.trim().replace(/^@/, "").normalize("NFKC").toLowerCase()
}

const usernamePattern = /^(?!\.)(?!.*\.\.)(?!.*\.$)[a-z0-9._]{3,32}$/

export const usernameSchema = z
  .string()
  .transform(normalizeUsername)
  .refine((value) => usernamePattern.test(value), {
    message: "validation.username",
  })

export const passwordSchema = z
  .string()
  .refine((value) => [...value].length >= 12, {
    message: "validation.passwordMin",
  })
  .refine((value) => [...value].length <= 128, {
    message: "validation.passwordMax",
  })

export const displayNameSchema = z
  .string()
  .trim()
  .min(1, "validation.required")
  .max(64, "validation.displayNameMax")

export const loginSchema = z.object({
  username: z.string().trim().min(1, "validation.required"),
  password: z.string().min(1, "validation.required"),
})

export const registerSchema = z
  .object({
    username: usernameSchema,
    displayName: displayNameSchema,
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "validation.passwordMismatch",
  })

export const recoverSchema = z.object({
  username: z.string().trim().min(1, "validation.required"),
  recoveryCode: z
    .string()
    .trim()
    .regex(/^[a-z2-9]{5}-?[a-z2-9]{5}$/i, "validation.recoveryCode"),
  newPassword: passwordSchema,
})

export const forgotPasswordSchema = z.object({
  username: z.string().trim().min(1, "validation.required"),
})

export const resetPasswordSchema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "validation.passwordMismatch",
  })

export type LoginValues = z.input<typeof loginSchema>
export type RegisterValues = z.input<typeof registerSchema>
export type RecoverValues = z.input<typeof recoverSchema>
export type ForgotPasswordValues = z.input<typeof forgotPasswordSchema>
export type ResetPasswordValues = z.input<typeof resetPasswordSchema>
