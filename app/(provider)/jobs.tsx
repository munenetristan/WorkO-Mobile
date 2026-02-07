import { StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function ProviderJobsScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">Job Queue</ThemedText>
      <ThemedText style={styles.subtitle}>
        Accept or reject incoming broadcasts and track active jobs.
      </ThemedText>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 12,
  },
  subtitle: {
    color: '#6b7280',
  },
});
