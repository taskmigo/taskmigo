import * as z from "zod";

export const messageSchema = z.strictObject({
  code: z.string(),
  text: z.string(),
});

export const apiErrorSchema = z.strictObject({
  code: z.string().nullable().optional(),
  message: z.string().nullable().optional(),
  formErrors: z.record(z.string(), z.string()).nullable().optional(),
});

export const executionSchema = z.strictObject({
  startedAt: z.iso.datetime(),
  duration: z.number().int().nonnegative(),
});

export const basicMetaSchema = z.strictObject({
  execution: executionSchema,
});

export const offsetSchema = z.strictObject({
  currentPage: z.number().int().positive(),
  pageSize: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});

export const offsetMetaSchema = z.strictObject({
  execution: executionSchema,
  pagination: z.strictObject({
    type: z.literal("offset"),
    offset: offsetSchema,
  }),
});

export const successApiResponseSchema = <StatusCode extends number, Data extends z.ZodType, Meta extends z.ZodType>(
  statusCode: StatusCode,
  data: Data,
  meta: Meta,
) =>
  z.strictObject({
    success: z.literal(true),
    statusCode: z.literal(statusCode),
    message: messageSchema,
    error: z.null(),
    meta,
    data,
  });

export type Message = z.infer<typeof messageSchema>;
export type ApiError = z.infer<typeof apiErrorSchema>;
export type Execution = z.infer<typeof executionSchema>;
export type BasicMeta = z.infer<typeof basicMetaSchema>;
export type Offset = z.infer<typeof offsetSchema>;
export type OffsetMeta = z.infer<typeof offsetMetaSchema>;
