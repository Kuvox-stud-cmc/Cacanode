import { StateView } from '@/components/feedback/state-view';

type EmptyStateProps = {
  actionLabel?: string;
  description: string;
  onAction?: () => void;
  title: string;
};

export function EmptyState(props: EmptyStateProps) {
  return <StateView {...props} />;
}
