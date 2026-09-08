import { describe, expect, it } from "vitest";
import { WEB_HEALTH } from "./health";

describe("web health payload", () => {
  it("identifies a healthy web service", () => {
    expect(WEB_HEALTH.status).toBe("UP");
    expect(WEB_HEALTH.service).toBe("web");
  });
});

