import { Redirect, Stack } from 'expo-router';

import { useAppSelector } from '@/store/hooks';

export default function AuthLayout() {
  const status = useAppSelector((state) => state.auth.status);
  if (status === 'authenticated') return <Redirect href="/dashboard" />;
  return <Stack screenOptions={{ headerShown: false }} />;
}
