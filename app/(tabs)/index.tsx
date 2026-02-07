import { useEffect, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  ActivityIndicator,
  StyleSheet,
} from "react-native";

type Service = {
  _id?: string;
  name?: string;
  genderTag?: string;
};

export default function HomeScreen() {
  const api = process.env.EXPO_PUBLIC_API_URL;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [services, setServices] = useState<Service[]>([]);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        setLoading(true);
        setError(null);

        if (!api) throw new Error("EXPO_PUBLIC_API_URL is missing in your .env file");

        const res = await fetch(`${api}/services`);
        if (!res.ok) throw new Error(`HTTP ${res.status} when calling /services`);

        const data = await res.json();
        const list = Array.isArray(data) ? data : [];

        if (!cancelled) setServices(list);
      } catch (e: any) {
        if (!cancelled) setError(e?.message || "Failed to load services");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [api]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>WorkO Mobile ✅</Text>
      <Text style={styles.subtitle}>API: {api || "(missing)"}</Text>

      {loading && (
        <View style={styles.block}>
          <ActivityIndicator />
          <Text style={styles.text}>Loading services…</Text>
        </View>
      )}

      {!!error && <Text style={styles.error}>Error: {error}</Text>}

      {!loading && !error && (
        <ScrollView style={styles.list}>
          <Text style={styles.text}>Services loaded: {services.length}</Text>

          {services.slice(0, 10).map((s, idx) => (
            <Text key={s._id || `${s.name}-${idx}`} style={styles.item}>
              • {s.name} ({s.genderTag})
            </Text>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#ffffff",
    paddingTop: 48,
    paddingHorizontal: 16,
  },
  title: {
    fontSize: 22,
    fontWeight: "700",
    color: "#000000",
  },
  subtitle: {
    marginTop: 6,
    color: "#333333",
  },
  block: {
    marginTop: 16,
  },
  text: {
    color: "#000000",
    marginTop: 8,
  },
  error: {
    marginTop: 16,
    color: "red",
  },
  list: {
    marginTop: 16,
  },
  item: {
    color: "#000000",
    marginTop: 6,
  },
});