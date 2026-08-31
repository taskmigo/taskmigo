export const testResourceKinds = ["organizations", "users", "roles", "groups", "projects"] as const;

export type TestResourceKind = (typeof testResourceKinds)[number];

export interface TestDataCleanupRequest {
  organizations: string[];
  users: string[];
  roles: string[];
  groups: string[];
  projects: string[];
}

type CleanupAction = () => Promise<void>;

type ResourceSets = Record<TestResourceKind, Set<string>>;

const createResourceSets = (): ResourceSets => ({
  organizations: new Set(),
  users: new Set(),
  roles: new Set(),
  groups: new Set(),
  projects: new Set(),
});

export class TestDataScope {
  private readonly resources = createResourceSets();
  private readonly deferred: CleanupAction[] = [];

  track(kind: TestResourceKind, id: string): void {
    this.resources[kind].add(id);
  }

  owns(kind: TestResourceKind, id: string): boolean {
    return this.resources[kind].has(id);
  }

  transfer(kind: TestResourceKind, id: string, target: TestDataScope): void {
    if (!this.resources[kind].delete(id)) {
      throw new Error(`Cannot transfer unowned ${kind} resource ${id}`);
    }
    target.track(kind, id);
  }

  transferAllTo(target: TestDataScope): void {
    for (const kind of testResourceKinds) {
      for (const id of this.resources[kind]) target.track(kind, id);
      this.resources[kind].clear();
    }
    target.deferred.push(...this.deferred);
    this.deferred.length = 0;
  }

  defer(action: CleanupAction): void {
    this.deferred.push(action);
  }

  async runDeferred(): Promise<unknown[]> {
    const failures: unknown[] = [];
    while (this.deferred.length > 0) {
      const action = this.deferred.pop();
      if (!action) continue;
      try {
        await action();
      } catch (error) {
        failures.push(error);
      }
    }
    return failures;
  }

  snapshot(): TestDataCleanupRequest {
    return {
      organizations: [...this.resources.organizations],
      users: [...this.resources.users],
      roles: [...this.resources.roles],
      groups: [...this.resources.groups],
      projects: [...this.resources.projects],
    };
  }

  hasResources(): boolean {
    return testResourceKinds.some((kind) => this.resources[kind].size > 0);
  }

  clearResources(): void {
    for (const kind of testResourceKinds) this.resources[kind].clear();
  }
}
