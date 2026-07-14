import { z } from 'zod';

const publicUrlSchema = z
  .string({ error: 'is required' })
  .trim()
  .url('must be an absolute URL')
  .refine((value) => value.startsWith('http://') || value.startsWith('https://'), {
    message: 'must use HTTP or HTTPS',
  });

const publicEnvSchema = z.object({
  EXPO_PUBLIC_API_BASE_URL: publicUrlSchema,
  EXPO_PUBLIC_AI_API_BASE_URL: publicUrlSchema,
});

type RawPublicEnv = {
  EXPO_PUBLIC_API_BASE_URL: string | undefined;
  EXPO_PUBLIC_AI_API_BASE_URL: string | undefined;
};

type ParsePublicEnvOptions = {
  allowInsecureHttp: boolean;
};

function removeTrailingSlashes(value: string) {
  return value.replace(/\/+$/, '');
}

export function parsePublicEnv(
  raw: RawPublicEnv,
  { allowInsecureHttp }: ParsePublicEnvOptions,
) {
  const parsed = publicEnvSchema.safeParse(raw);

  if (!parsed.success) {
    const names = parsed.error.issues
      .map((issue) => issue.path.join('.'))
      .filter(Boolean)
      .join(', ');
    throw new Error(`Invalid public mobile environment configuration: ${names}`);
  }

  const values = {
    apiBaseUrl: removeTrailingSlashes(parsed.data.EXPO_PUBLIC_API_BASE_URL),
    aiApiBaseUrl: removeTrailingSlashes(parsed.data.EXPO_PUBLIC_AI_API_BASE_URL),
  };

  if (
    !allowInsecureHttp &&
    (values.apiBaseUrl.startsWith('http://') || values.aiApiBaseUrl.startsWith('http://'))
  ) {
    throw new Error('Production mobile API URLs must use HTTPS');
  }

  return values;
}

export const env = parsePublicEnv(
  {
    EXPO_PUBLIC_API_BASE_URL:
      process.env.EXPO_PUBLIC_API_BASE_URL ??
      (process.env.NODE_ENV === 'test' ? 'http://localhost:8080/api/v1' : undefined),
    EXPO_PUBLIC_AI_API_BASE_URL:
      process.env.EXPO_PUBLIC_AI_API_BASE_URL ??
      (process.env.NODE_ENV === 'test' ? 'http://localhost:8000/api/v1' : undefined),
  },
  { allowInsecureHttp: __DEV__ },
);
