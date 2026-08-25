import type { IncomingMessage, ServerResponse } from 'node:http'

// The request/response contract every handler in this directory is written
// against. This used to be borrowed from @vercel/node; now that the VPS runner
// in server/main.ts is the only thing serving these files, the contract lives
// here instead — it is exactly the shape that runner constructs.
//
// Both types are structural and erased at compile time, so they add nothing to
// the deployed output.

export type ApiRequest = IncomingMessage & {
  /** Parsed JSON body, or `{}` when the request carried none. */
  body: any
  /** Decoded query-string parameters. */
  query: Record<string, string | string[]>
  cookies: Record<string, string>
}

export type ApiResponse = ServerResponse & {
  /** Sets the status code and returns itself, so `.status(x).json(y)` chains. */
  status: (code: number) => ApiResponse
  /** Serialises `body` as JSON, sets the content type, and ends the response. */
  json: (body: unknown) => void
}
