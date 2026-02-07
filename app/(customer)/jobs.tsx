import { StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function CustomerJobsScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">Your Jobs</ThemedText>
      <ThemedText style={styles.subtitle}>
        Track active requests and view your service history.
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
