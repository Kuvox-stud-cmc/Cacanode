import { parsePublicEnv } from '@/constants/env';

const validEnv = {
  EXPO_PUBLIC_API_BASE_URL: 'http://localhost:8080/api/v1/',
  EXPO_PUBLIC_AI_API_BASE_URL: 'http://localhost:8000/api/v1///',
};

describe('parsePublicEnv', () => {
  it('validates and removes trailing slashes from public URLs', () => {
    expect(parsePublicEnv(validEnv, { allowInsecureHttp: true })).toEqual({
      apiBaseUrl: 'http://localhost:8080/api/v1',
      aiApiBaseUrl: 'http://localhost:8000/api/v1',
    });
  });

  it('reports missing variables without including their values', () => {
    expect(() =>
      parsePublicEnv(
        {
          EXPO_PUBLIC_API_BASE_URL: undefined,
          EXPO_PUBLIC_AI_API_BASE_URL: undefined,
        },
        { allowInsecureHttp: true },
      ),
    ).toThrow(
      'Invalid public mobile environment configuration: EXPO_PUBLIC_API_BASE_URL, EXPO_PUBLIC_AI_API_BASE_URL',
    );
  });

  it('rejects invalid URLs', () => {
    expect(() =>
      parsePublicEnv(
        { ...validEnv, EXPO_PUBLIC_API_BASE_URL: 'localhost:8080' },
        { allowInsecureHttp: true },
      ),
    ).toThrow('Invalid public mobile environment configuration: EXPO_PUBLIC_API_BASE_URL');
  });

  it('requires HTTPS when insecure HTTP is disabled', () => {
    expect(() => parsePublicEnv(validEnv, { allowInsecureHttp: false })).toThrow(
      'Production mobile API URLs must use HTTPS',
    );
  });
});
