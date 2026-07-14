import { StateView } from '@/components/feedback/state-view';

type OfflineStateProps = {
  onRetry?: () => void;
};

export function OfflineState({ onRetry }: OfflineStateProps) {
  return (
    <StateView
      actionLabel={onRetry ? 'Try again' : undefined}
      description="Check your connection and try again."
      onAction={onRetry}
      title="You are offline"
    />
  );
}
