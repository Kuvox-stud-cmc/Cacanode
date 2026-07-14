import { HeaderAvatarButton } from '@/features/navigation/components/header-avatar-button';
import { useAccountMenu } from '@/features/navigation/components/account-menu-provider';
import { useAppSelector } from '@/store/hooks';

export function AuthenticatedHeaderRight() {
  const { open } = useAccountMenu();
  const user = useAppSelector((state) => state.auth.user);
  return <HeaderAvatarButton email={user?.email} fullName={user?.fullName} onPress={open} />;
}
