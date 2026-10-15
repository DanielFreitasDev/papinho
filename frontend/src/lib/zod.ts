import { z } from "zod"

/**
 * O zod 4 decide se compila validadores com `new Function` ao construir cada schema, testando
 * `new Function("")`. A CSP estrita (sem unsafe-eval) bloqueia e registra uma violação. O modo jitless
 * evita o teste, mas precisa valer antes do primeiro schema: por isso todo o código importa `z` daqui
 * (regra no-restricted-imports do ESLint), nunca direto de "zod".
 */
z.config({ jitless: true })

export { z }
