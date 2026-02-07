import { Stack } from 'expo-router';

export default function AuthLayout() {
  return (
    <Stack
      screenOptions={{
        headerShown: true,
        headerTitleAlign: 'center',
      }}>
      <Stack.Screen name="country" options={{ title: 'Select Country' }} />
      <Stack.Screen name="phone" options={{ title: 'Phone Number' }} />
      <Stack.Screen name="otp" options={{ title: 'Verify OTP' }} />
      <Stack.Screen name="register-customer" options={{ title: 'Customer Registration' }} />
      <Stack.Screen name="register-provider" options={{ title: 'Provider Registration' }} />
    </Stack>
  );
}
