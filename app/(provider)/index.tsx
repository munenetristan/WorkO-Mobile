import { Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function ProviderHomeScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">Go Online</ThemedText>
      <ThemedText style={styles.subtitle}>
        Toggle availability to start receiving job requests.
      </ThemedText>
      <Pressable style={styles.primaryButton}>
        <ThemedText style={styles.primaryButtonText}>Enable Online Status</ThemedText>
      </Pressable>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 16,
  },
  subtitle: {
    color: '#6b7280',
  },
  primaryButton: {
    backgroundColor: '#16a34a',
    paddingVertical: 16,
    alignItems: 'center',
    borderRadius: 8,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
  },
});
