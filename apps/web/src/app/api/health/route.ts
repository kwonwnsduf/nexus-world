import { WEB_HEALTH } from "@/lib/health";

export function GET() {
  return Response.json(WEB_HEALTH, {
    headers: { "Cache-Control": "no-store" },
  });
}

