import { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { services } from '@/constants/services';
import { useAuth } from '@/hooks/use-auth';
import { apiRequest } from '@/lib/api';

type RegisterResponse = {
  authToken: string;
  role: 'customer' | 'provider';
};

const genderOptions = [
  { id: 'M', label: 'Male' },
  { id: 'W', label: 'Female' },
  { id: 'B', label: 'Non-binary' },
];

const nationalityOptions = [
  { id: 'Citizen', label: 'Citizen' },
  { id: 'Other', label: 'Other' },
];

export default function RegisterProviderScreen() {
  const { state, setAuthToken } = useAuth();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [dob, setDob] = useState('');
  const [gender, setGender] = useState('');
  const [nationalityType, setNationalityType] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const [selectedServices, setSelectedServices] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const availableServices = useMemo(
    () => services.filter((service) => service.enabled).sort((a, b) => a.sortOrder - b.sortOrder),
    []
  );

  const toggleService = (serviceId: string) => {
    setSelectedServices((prev) => {
      if (prev.includes(serviceId)) {
        return prev.filter((id) => id !== serviceId);
      }
      if (prev.length >= 3) {
        return prev;
      }
      return [...prev, serviceId];
    });
  };

  const handleRegister = async () => {
    if (!state.otpToken || !state.country) {
      setError('Missing verification token.');
      return;
    }
    if (!firstName || !lastName || !email || !password || !dob || !gender || !nationalityType) {
      setError('Please complete all required fields.');
      return;
    }
    if (!idNumber || selectedServices.length === 0) {
      setError('Select 1 to 3 services and provide ID/Passport number.');
      return;
    }

    setError('');
    setLoading(true);
    try {
      const response = await apiRequest<RegisterResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          role: 'provider',
          otpToken: state.otpToken,
          firstName,
          lastName,
          email,
          password,
          phone: `${state.country.dialCode}${state.phoneNumber}`,
          countryCode: state.country.iso2,
          dob,
          gender,
          nationalityType,
          idOrPassportNumber: idNumber,
          services: selectedServices,
        }),
      });
      await setAuthToken(response.authToken);
      router.replace('/(provider)');
    } catch (err) {
      setError('Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Provider registration
      </ThemedText>
      <ThemedText style={styles.subtitle}>Select up to 3 services you can offer.</ThemedText>

      <View style={styles.inputGroup}>
        <TextInput
          style={styles.input}
          placeholder="First name"
          value={firstName}
          onChangeText={setFirstName}
        />
        <TextInput
          style={styles.input}
          placeholder="Last name"
          value={lastName}
          onChangeText={setLastName}
        />
        <TextInput
          style={styles.input}
          placeholder="Email"
          keyboardType="email-address"
          autoCapitalize="none"
          value={email}
          onChangeText={setEmail}
        />
        <TextInput
          style={styles.input}
          placeholder="Password"
          secureTextEntry
          value={password}
          onChangeText={setPassword}
        />
        <TextInput
          style={styles.input}
          placeholder="Date of birth (YYYY-MM-DD)"
          value={dob}
          onChangeText={setDob}
        />
      </View>

      <View style={styles.optionRow}>
        {genderOptions.map((option) => (
          <Pressable
            key={option.id}
            style={[styles.optionChip, gender === option.id && styles.optionChipActive]}
            onPress={() => setGender(option.id)}>
            <ThemedText style={gender === option.id ? styles.optionTextActive : styles.optionText}>
              {option.label}
            </ThemedText>
          </Pressable>
        ))}
      </View>

      <View style={styles.optionRow}>
        {nationalityOptions.map((option) => (
          <Pressable
            key={option.id}
            style={[styles.optionChip, nationalityType === option.id && styles.optionChipActive]}
            onPress={() => setNationalityType(option.id)}>
            <ThemedText
              style={nationalityType === option.id ? styles.optionTextActive : styles.optionText}>
              {option.label}
            </ThemedText>
          </Pressable>
        ))}
      </View>

      <TextInput
        style={styles.input}
        placeholder="ID/Passport number"
        value={idNumber}
        onChangeText={setIdNumber}
      />

      <View style={styles.lockedField}>
        <ThemedText>{`${state.country?.dialCode ?? ''}${state.phoneNumber}`}</ThemedText>
      </View>

      <FlatList
        data={availableServices}
        keyExtractor={(item) => item.id}
        numColumns={2}
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.serviceList}
        renderItem={({ item }) => {
          const isSelected = selectedServices.includes(item.id);
          return (
            <Pressable
              style={[styles.serviceButton, isSelected && styles.serviceButtonActive]}
              onPress={() => toggleService(item.id)}>
              <ThemedText
                style={isSelected ? styles.serviceTextActive : styles.serviceText}
                type="defaultSemiBold">
                {item.name}
              </ThemedText>
            </Pressable>
          );
        }}
      />

      {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
      <Pressable style={styles.primaryButton} onPress={handleRegister} disabled={loading}>
        <ThemedText style={styles.primaryButtonText}>
          {loading ? 'Submitting...' : 'Submit for Review'}
        </ThemedText>
      </Pressable>
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
    fontSize: 22,
  },
  subtitle: {
    color: '#6b7280',
  },
  inputGroup: {
    gap: 10,
  },
  input: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 12,
  },
  optionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  optionChip: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 20,
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  optionChipActive: {
    borderColor: '#2563eb',
    backgroundColor: '#eff6ff',
  },
  optionText: {
    color: '#374151',
  },
  optionTextActive: {
    color: '#2563eb',
  },
  lockedField: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 12,
    backgroundColor: '#f3f4f6',
  },
  serviceList: {
    paddingTop: 8,
    paddingBottom: 16,
  },
  row: {
    gap: 12,
    marginBottom: 12,
  },
  serviceButton: {
    flex: 1,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    paddingVertical: 12,
    paddingHorizontal: 8,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 64,
  },
  serviceButtonActive: {
    borderColor: '#2563eb',
    backgroundColor: '#eff6ff',
  },
  serviceText: {
    textAlign: 'center',
    color: '#374151',
  },
  serviceTextActive: {
    textAlign: 'center',
    color: '#2563eb',
  },
  error: {
    color: '#dc2626',
  },
  primaryButton: {
    marginTop: 8,
    backgroundColor: '#2563eb',
    paddingVertical: 16,
    alignItems: 'center',
    borderRadius: 8,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
  },
});
