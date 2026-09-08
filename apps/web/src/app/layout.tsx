import type { Metadata } from "next";
import "./styles.css";

export const metadata: Metadata = {
  title: "NEXUS WORLD",
  description: "Economic civilization and supply-chain digital twin",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

