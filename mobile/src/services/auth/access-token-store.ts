let accessToken: string | null = null;

export const accessTokenStore = {
  get(): string | null {
    return accessToken;
  },
  set(value: string | null): void {
    accessToken = value;
  },
};
