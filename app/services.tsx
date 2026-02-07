import { FlatList, Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { services } from '@/constants/services';

export default function ServicesScreen() {
  const sortedServices = services
    .filter((service) => service.enabled)
    .sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Choose a Service
      </ThemedText>
      <ThemedText style={styles.subtitle}>
        Pick one of the available services to request help.
      </ThemedText>
      <FlatList
        data={sortedServices}
        keyExtractor={(item) => item.id}
        numColumns={2}
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.listContent}
        renderItem={({ item }) => (
          <Pressable style={styles.serviceButton}>
            <ThemedText type="defaultSemiBold" style={styles.buttonText}>
              {item.name}
            </ThemedText>
          </Pressable>
        )}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  title: {
    marginBottom: 4,
  },
  subtitle: {
    marginBottom: 16,
  },
  listContent: {
    paddingBottom: 24,
  },
  row: {
    gap: 12,
    marginBottom: 12,
  },
  serviceButton: {
    flex: 1,
    borderRadius: 0,
    borderWidth: 1,
    borderColor: '#1f2937',
    paddingVertical: 18,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 70,
  },
  buttonText: {
    textAlign: 'center',
  },
});
