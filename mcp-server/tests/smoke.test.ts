import { describe, it, expect } from 'vitest';

describe('Smoke Tests', () => {
  it('test infrastructure works', () => {
    expect(true).toBe(true);
  });

  it('node version is correct', () => {
    const nodeVersion = parseInt(process.version.slice(1).split('.')[0], 10);
    expect(nodeVersion).toBeGreaterThanOrEqual(18);
  });
});
