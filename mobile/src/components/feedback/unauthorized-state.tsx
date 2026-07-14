import { StateView } from '@/components/feedback/state-view';

type UnauthorizedStateProps = {
  onAction?: () => void;
};

export function UnauthorizedState({ onAction }: UnauthorizedStateProps) {
  return (
    <StateView
      actionLabel={onAction ? 'Go back' : undefined}
      description="Your account does not have permission to view this content."
      onAction={onAction}
      title="Access denied"
    />
  );
}
