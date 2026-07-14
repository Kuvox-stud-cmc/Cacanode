import { useRef, useState } from 'react';
import {
  FlatList,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';

import { EmptyState } from '@/components/feedback/empty-state';
import { ErrorState } from '@/components/feedback/error-state';
import { LoadingState } from '@/components/feedback/loading-state';
import { RetryPanel } from '@/components/feedback/retry-panel';
import { Screen } from '@/components/layout/screen';
import { AppText } from '@/components/ui/app-text';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { radii, spacing } from '@/constants/theme';
import { ChatHistorySheet } from '@/features/chat/components/chat-history-sheet';
import { ChatMessageBubble } from '@/features/chat/components/chat-message-bubble';
import { CitationDetailSheet } from '@/features/chat/components/citation-detail-sheet';
import { CHAT_MAX_LENGTH } from '@/features/chat/model/chat-state';
import { useChatController } from '@/features/chat/model/use-chat-controller';
import type { ChatCitation, PlaygroundSession, TranscriptMessage } from '@/features/chat/types';
import { useAppTheme } from '@/hooks/use-app-theme';

export function ChatScreen() {
  const theme = useAppTheme();
  const controller = useChatController();
  const listRef = useRef<FlatList<TranscriptMessage>>(null);
  const [historyVisible, setHistoryVisible] = useState(false);
  const [citation, setCitation] = useState<ChatCitation | null>(null);
  const [hideTarget, setHideTarget] = useState<PlaygroundSession | null>(null);

  if (controller.workspaceLoading && !controller.workspace) {
    return (
      <Screen edges={['right', 'bottom', 'left']} style={styles.centered}>
        <LoadingState
          description="Preparing your tenant chat workspace."
          title="Loading employee chat"
        />
      </Screen>
    );
  }

  if (!controller.workspace) {
    return (
      <Screen edges={['right', 'bottom', 'left']} style={styles.centered}>
        <ErrorState
          description={controller.workspaceError ?? undefined}
          onRetry={() => void controller.retryWorkspace()}
          title="Unable to open employee chat"
        />
      </Screen>
    );
  }

  const selectHistory = (session: PlaygroundSession) => {
    controller.selectSession(session.id);
    setHistoryVisible(false);
  };

  const confirmHide = async () => {
    if (!hideTarget) return;
    const hidden = await controller.hideSession(hideTarget);
    if (hidden) setHideTarget(null);
  };

  const submit = () => {
    if (!controller.canSend) return;
    Keyboard.dismiss();
    void controller.send();
  };

  return (
    <Screen edges={['right', 'bottom', 'left']} style={styles.screen}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 88 : 0}
        style={styles.flex}
        testID="chat-keyboard-layout">
        <View style={[styles.actions, { borderBottomColor: theme.colors.border }]}>
          <Button
            accessibilityLabel="Open conversation history"
            disabled={controller.sending}
            onPress={() => setHistoryVisible(true)}
            style={styles.actionButton}
            variant="secondary">
            History
          </Button>
          <Button
            accessibilityLabel="Start new chat"
            disabled={controller.sending}
            onPress={controller.startNewChat}
            style={styles.actionButton}
            variant="secondary">
            New Chat
          </Button>
        </View>

        <FlatList
          contentContainerStyle={[
            styles.transcript,
            controller.state.messages.length === 0 ? styles.emptyTranscript : undefined,
          ]}
          data={controller.state.messages}
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          keyExtractor={(message) => message.id}
          ListEmptyComponent={
            <TranscriptEmptyState
              historyError={controller.state.historyError}
              historyLoading={controller.state.historyStatus === 'loading' || !controller.state.selectionInitialized}
              onReload={() => void controller.reloadTranscript()}
              welcomeMessage={controller.workspace.chatbot.welcomeMessage}
            />
          }
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
          ref={listRef}
          renderItem={({ item }) => (
            <ChatMessageBubble
              message={item}
              onOpenCitation={setCitation}
              onReload={() => void controller.reloadTranscript()}
              reloadDisabled={!controller.state.selectedSessionId || controller.sending}
            />
          )}
          showsVerticalScrollIndicator={false}
          testID="chat-transcript"
        />

        <View style={[styles.composer, { backgroundColor: theme.colors.surface, borderTopColor: theme.colors.border }]}>
          <TextInput
            accessibilityLabel="Message"
            accessibilityState={{ disabled: controller.workspaceLoading || controller.sending }}
            editable={!controller.workspaceLoading && !controller.sending}
            maxLength={CHAT_MAX_LENGTH}
            multiline
            onChangeText={controller.setDraft}
            placeholder="Ask about your workspace documents"
            placeholderTextColor={theme.colors.textMuted}
            scrollEnabled
            style={[
              styles.input,
              {
                backgroundColor: theme.colors.background,
                borderColor: theme.colors.border,
                color: theme.colors.text,
              },
            ]}
            textAlignVertical="top"
            value={controller.state.draft}
          />
          <View style={styles.composerFooter}>
            <AppText muted variant="caption">
              {controller.state.draft.length.toLocaleString()} / {CHAT_MAX_LENGTH.toLocaleString()}
            </AppText>
            <Button
              accessibilityLabel="Send message"
              disabled={!controller.canSend}
              loading={controller.sending}
              onPress={submit}
              style={styles.sendButton}>
              Send
            </Button>
          </View>
        </View>
      </KeyboardAvoidingView>

      <ChatHistorySheet
        activeSessionId={controller.state.selectedSessionId}
        error={controller.sessionsError}
        hideError={controller.hideError}
        hiding={controller.hiding}
        loading={controller.sessionsLoading}
        onDismiss={() => setHistoryVisible(false)}
        onHide={setHideTarget}
        onRetry={() => void controller.retrySessions()}
        onSelect={selectHistory}
        sessions={controller.sessions}
        visible={historyVisible}
      />

      <CitationDetailSheet citation={citation} onDismiss={() => setCitation(null)} />

      <Dialog
        actions={
          <>
            <Button
              disabled={controller.hiding}
              onPress={() => setHideTarget(null)}
              variant="secondary">
              Cancel
            </Button>
            <Button loading={controller.hiding} onPress={() => void confirmHide()} variant="danger">
              Hide conversation
            </Button>
          </>
        }
        description="This removes the conversation from your history. Its messages may still be retained for workspace analytics."
        onDismiss={() => setHideTarget(null)}
        title={`Hide “${hideTarget?.title ?? 'conversation'}”?`}
        visible={Boolean(hideTarget)}
      />
    </Screen>
  );
}

function TranscriptEmptyState({
  historyError,
  historyLoading,
  onReload,
  welcomeMessage,
}: {
  historyError: string | null;
  historyLoading: boolean;
  onReload: () => void;
  welcomeMessage: string;
}) {
  if (historyLoading) {
    return <LoadingState description="Retrieving the complete transcript." title="Loading conversation" />;
  }
  if (historyError) {
    return <RetryPanel description={historyError} onRetry={onReload} title="Unable to load conversation" />;
  }
  return (
    <EmptyState
      description={welcomeMessage || 'Ask a question about the documents in your employee workspace.'}
      title="Start a conversation"
    />
  );
}

const styles = StyleSheet.create({
  actionButton: { flex: 1 },
  actions: { borderBottomWidth: StyleSheet.hairlineWidth, flexDirection: 'row', gap: spacing.md, padding: spacing.lg },
  centered: { justifyContent: 'center' },
  composer: { borderTopWidth: StyleSheet.hairlineWidth, gap: spacing.sm, padding: spacing.lg },
  composerFooter: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  emptyTranscript: { flexGrow: 1, justifyContent: 'center' },
  flex: { flex: 1 },
  input: { borderRadius: radii.md, borderWidth: 1, fontSize: 16, maxHeight: 144, minHeight: 52, paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  screen: { paddingHorizontal: 0 },
  sendButton: { minWidth: 88 },
  transcript: { gap: spacing.lg, paddingVertical: spacing.xl },
});
