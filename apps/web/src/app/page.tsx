import { PlatformStatusCard } from "@/components/platform-status";

const services = ["Evidence-grounded worlds", "Deterministic simulation", "Parallel policy comparison"];

export default function Home() {
  return (
    <main>
      <p className="eyebrow">DAY 2 CONTRACT</p>
      <h1>NEXUS WORLD</h1>
      <p className="lede">Agentic Economic Civilization &amp; Supply-Chain Digital Twin</p>
      <ul>
        {services.map((service) => (
          <li key={service}>{service}</li>
        ))}
      </ul>
      <PlatformStatusCard />
    </main>
  );
}
