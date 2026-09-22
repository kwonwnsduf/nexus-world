from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, model_validator


class CompanyState(BaseModel):
    model_config = ConfigDict(extra="forbid")

    companyId: str = Field(min_length=1, max_length=160)
    industry: str = Field(min_length=1, max_length=80)
    baselineProduction: float = Field(ge=0)
    capacity: float = Field(ge=0)
    inventory: float = Field(ge=0)
    demand: float = Field(ge=0)
    unitPrice: float = Field(gt=0)
    unitVariableCost: float = Field(ge=0)
    workforce: float = Field(ge=0)
    wagePerWorker: float = Field(ge=0)
    productivity: float = Field(gt=0)


class SupplyLink(BaseModel):
    model_config = ConfigDict(extra="forbid")

    supplierId: str = Field(min_length=1, max_length=160)
    customerId: str = Field(min_length=1, max_length=160)
    inputUnitsPerOutput: float = Field(gt=0)
    maxFlow: float = Field(ge=0)
    unitCost: float = Field(ge=0)


class IndustrialShock(BaseModel):
    model_config = ConfigDict(extra="forbid")

    companyId: str = Field(min_length=1, max_length=160)
    turn: int = Field(ge=1, le=120)
    supplyMultiplier: float = Field(default=1, ge=0, le=2)
    demandMultiplier: float = Field(default=1, ge=0, le=2)
    capacityMultiplier: float = Field(default=1, ge=0, le=2)
    priceMultiplier: float = Field(default=1, ge=0.25, le=4)


class SimulationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: str = Field(pattern="^v1$")
    seed: int
    turns: int = Field(ge=1, le=120)
    companies: tuple[CompanyState, ...] = Field(min_length=1, max_length=5_000)
    supplyLinks: tuple[SupplyLink, ...] = Field(default=(), max_length=20_000)
    shocks: tuple[IndustrialShock, ...] = Field(default=(), max_length=20_000)

    @model_validator(mode="after")
    def validate_graph(self) -> SimulationRequest:
        company_ids = [company.companyId for company in self.companies]
        if len(company_ids) != len(set(company_ids)):
            raise ValueError("companyId values must be unique")
        known = set(company_ids)
        for link in self.supplyLinks:
            if link.supplierId not in known or link.customerId not in known:
                raise ValueError("supply links must reference known companies")
            if link.supplierId == link.customerId:
                raise ValueError("self-referencing supply links are not allowed")
        if len({(link.supplierId, link.customerId) for link in self.supplyLinks}) != len(
            self.supplyLinks
        ):
            raise ValueError("duplicate supply links are not allowed")
        if any(shock.companyId not in known for shock in self.shocks):
            raise ValueError("shocks must reference known companies")
        if any(shock.turn > self.turns for shock in self.shocks):
            raise ValueError("shock turn cannot exceed requested turns")
        if len({(shock.companyId, shock.turn) for shock in self.shocks}) != len(self.shocks):
            raise ValueError("duplicate company shock in the same turn is not allowed")
        return self


class CompanyTurn(BaseModel):
    companyId: str
    production: float
    unitsSold: float
    endingInventory: float
    unmetDemand: float
    unitPrice: float
    revenue: float
    variableCost: float
    wageBill: float
    operatingProfit: float
    nextTurnDemand: float
    nextTurnLaborDemand: float


class FlowTurn(BaseModel):
    supplierId: str
    customerId: str
    quantity: float
    value: float


class TurnSnapshot(BaseModel):
    turn: int
    companies: tuple[CompanyTurn, ...]
    flows: tuple[FlowTurn, ...]
    metrics: dict[str, float]
    invariantViolations: tuple[str, ...]


class SimulationResult(BaseModel):
    contractVersion: str = "v1"
    seed: int
    turns: int
    snapshots: tuple[TurnSnapshot, ...]
    finalState: tuple[CompanyState, ...]
    resultHash: str
    invariants: dict[str, bool]
