import { useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { fallbackCountries } from '@/constants/countries';
import { CountryOption } from '@/context/auth-context';
import { useAuth } from '@/hooks/use-auth';
import { apiRequest } from '@/lib/api';

const detectCountryIso = () => {
  const locale = Intl.DateTimeFormat().resolvedOptions().locale;
  const parts = locale.split('-');
  return parts[1]?.toUpperCase() ?? null;
};

export default function CountryScreen() {
  const { setCountry, state } = useAuth();
  const [countries, setCountries] = useState<CountryOption[]>(fallbackCountries);
  const [loading, setLoading] = useState(false);
  const detectedIso = useMemo(() => detectCountryIso(), []);

  useEffect(() => {
    const loadCountries = async () => {
      setLoading(true);
      try {
        const data = await apiRequest<{ countries: CountryOption[] }>('/api/v1/countries?enabled=true');
        if (data?.countries?.length) {
          setCountries(data.countries);
        }
      } catch (error) {
        setCountries(fallbackCountries);
      } finally {
        setLoading(false);
      }
    };

    loadCountries();
  }, []);

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Choose your country
      </ThemedText>
      <ThemedText style={styles.subtitle}>
        This sets your dialing code and available services.
      </ThemedText>
      {loading ? (
        <ThemedText>Loading countries...</ThemedText>
      ) : (
        <FlatList
          data={countries}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => {
            const isDetected = detectedIso === item.iso2;
            const isSelected = state.country?.id === item.id;
            return (
              <Pressable
                style={[styles.countryRow, (isDetected || isSelected) && styles.highlightRow]}
                onPress={() => {
                  setCountry(item);
                  router.push('/(auth)/phone');
                }}>
                <View style={styles.countryLeft}>
                  <ThemedText style={styles.flag}>{item.flag}</ThemedText>
                  <View>
                    <ThemedText type="defaultSemiBold">{item.name}</ThemedText>
                    <ThemedText style={styles.isoText}>
                      {item.iso2} · {item.dialCode}
                    </ThemedText>
                  </View>
                </View>
                {isDetected && <ThemedText style={styles.detected}>Detected</ThemedText>}
              </Pressable>
            );
          }}
        />
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    gap: 12,
  },
  title: {
    fontSize: 24,
  },
  subtitle: {
    color: '#6b7280',
  },
  list: {
    paddingTop: 8,
    gap: 12,
  },
  countryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 14,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
  },
  highlightRow: {
    borderColor: '#2563eb',
    backgroundColor: '#eff6ff',
  },
  countryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  flag: {
    fontSize: 24,
  },
  isoText: {
    color: '#6b7280',
    fontSize: 12,
  },
  detected: {
    color: '#2563eb',
    fontSize: 12,
  },
});
