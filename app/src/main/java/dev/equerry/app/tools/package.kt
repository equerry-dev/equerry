/**
 * Tool-calling: tool schema, the dispatcher that maps a ToolCall name+args to an
 * implementation, and the individual tool impls (spec §5). Unknown tool → clean error to
 * the model, never a crash.
 */
package dev.equerry.app.tools
