export const WEB_HEALTH = Object.freeze({
  status: "UP",
  service: "web",
  version: process.env.npm_package_version ?? "dev",
});

