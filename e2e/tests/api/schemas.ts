import { z } from "zod";

export const permissionSchema = z.enum([
  "project.read",
  "project.update",
  "project.members.read",
  "project.members.manage",
]);

export const permissionListSchema = z.array(permissionSchema);

export const createdResourceSchema = z.strictObject({ id: z.string().uuid() });
export type CreatedResource = z.infer<typeof createdResourceSchema>;

const executionSchema = z.strictObject({
  started_at: z.iso.datetime(),
  duration_ms: z.number().int().nonnegative(),
});

export const basicMetaSchema = z.strictObject({ execution: executionSchema });

export const cursorPaginationSchema = z.strictObject({
  type: z.literal("cursor"),
  cursor: z.strictObject({
    next_cursor: z.string().nullable(),
    prev_cursor: z.string().nullable(),
    has_more: z.boolean(),
  }),
});

export const cursorMetaSchema = z.strictObject({
  execution: executionSchema,
  pagination: cursorPaginationSchema,
});

export type CursorPagination = z.infer<typeof cursorPaginationSchema>;

export const projectHistoryEntrySchema = z.strictObject({
  id: z.string().uuid(),
  projectId: z.string().uuid(),
  action: z.enum([
    "PROJECT_CREATED",
    "PROJECT_UPDATED",
    "PROJECT_ARCHIVED",
    "MEMBER_JOINED",
    "MEMBER_ADDED",
    "MEMBER_LEFT",
    "MEMBER_REMOVED",
    "MEMBER_ROLES_CHANGED",
  ]),
  actor: z.strictObject({
    type: z.enum(["USER", "SERVICE", "SYSTEM"]),
    id: z.string(),
    displayName: z.string(),
  }),
  target: z
    .strictObject({
      type: z.enum(["PROJECT", "USER", "GROUP"]),
      id: z.string(),
      displayName: z.string(),
    })
    .nullable(),
  changes: z.array(
    z.strictObject({
      field: z.string(),
      before: z.unknown(),
      after: z.unknown(),
    }),
  ),
  data: z.record(z.string(), z.unknown()),
  occurredAt: z.iso.datetime(),
});

export type ProjectHistoryEntry = z.infer<typeof projectHistoryEntrySchema>;

export const oauthTokenSchema = z.strictObject({
  access_token: z.string().min(1),
  token_type: z.literal("Bearer"),
  expires_in: z.number().int().positive().optional(),
  scope: z.string().optional(),
});

export const oauthErrorSchema = z.strictObject({
  error: z.string(),
  error_description: z.string().optional(),
  error_uri: z.string().url().optional(),
});

export const successEnvelopeSchema = <DataSchema extends z.ZodType, MetaSchema extends z.ZodType>(
  dataSchema: DataSchema,
  metaSchema: MetaSchema,
  status: number,
  messageCode: string,
) =>
  z.strictObject({
    success: z.literal(true),
    status_code: z.literal(status),
    message: z.strictObject({ code: z.literal(messageCode), text: z.string() }),
    error: z.null(),
    meta: metaSchema,
    data: dataSchema,
  });

export const failureEnvelopeSchema = (status: number, messageCode: string, errorCode: string) =>
  z.strictObject({
    success: z.literal(false),
    status_code: z.literal(status),
    message: z.strictObject({ code: z.literal(messageCode), text: z.string() }),
    error: z.strictObject({
      code: z.literal(errorCode),
      message: z.string(),
      form_errors: z.record(z.string(), z.string()).optional(),
    }),
    meta: basicMetaSchema,
    data: z.null(),
  });
