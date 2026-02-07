# WorkO Mobile (Expo)

WorkO is a global piece-job marketplace with customer and provider flows in a single Expo React Native app.

## Get started

1. Install dependencies

   ```bash
   npm install
   ```

2. Create an environment file

   ```bash
   cp .env.example .env
   ```

   Update `EXPO_PUBLIC_API_URL` with the backend base URL. **Note:** On a physical phone, you cannot use
   `localhost` or `127.0.0.1`. Use your machine's LAN IP (example: `http://192.168.1.50:5000/api/v1`).

3. Start the app

   ```bash
   npx expo start
   ```

In the output, you'll find options to open the app in a

- [development build](https://docs.expo.dev/develop/development-builds/introduction/)
- [Android emulator](https://docs.expo.dev/workflow/android-studio-emulator/)
- [iOS simulator](https://docs.expo.dev/workflow/ios-simulator/)
- [Expo Go](https://expo.dev/go), a limited sandbox for trying out app development with Expo

## Build for Android (EAS)

1. Install the EAS CLI if needed: `npm install -g eas-cli`
2. Configure EAS: `eas build:configure`
3. Trigger a build: `eas build --platform android`

## Learn more

To learn more about developing your project with Expo, look at the following resources:

- [Expo documentation](https://docs.expo.dev/): Learn fundamentals, or go into advanced topics with our [guides](https://docs.expo.dev/guides).
- [Learn Expo tutorial](https://docs.expo.dev/tutorial/introduction/): Follow a step-by-step tutorial where you'll create a project that runs on Android, iOS, and the web.

## Join the community

Join our community of developers creating universal apps.

- [Expo on GitHub](https://github.com/expo/expo): View our open source platform and contribute.
- [Discord community](https://chat.expo.dev): Chat with Expo users and ask questions.
