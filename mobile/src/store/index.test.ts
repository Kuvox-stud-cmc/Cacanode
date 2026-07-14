import { aiApi, springApi } from '@/services/api/api';
import { createAppStore } from '@/store';

describe('application store', () => {
  it('registers both API slices and accepts their reset actions', () => {
    const testStore = createAppStore();
    const state = testStore.getState();

    expect(state.springApi).toBeDefined();
    expect(state.aiApi).toBeDefined();
    expect(state.auth.status).toBe('bootstrapping');
    expect(JSON.stringify(state.auth)).not.toContain('Token');
    expect(() => testStore.dispatch(springApi.util.resetApiState())).not.toThrow();
    expect(() => testStore.dispatch(aiApi.util.resetApiState())).not.toThrow();
  });
});
