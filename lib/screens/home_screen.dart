import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/monitor_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _monitorService = MonitorService();
  final _formKey = GlobalKey<FormState>();

  bool _isLoading = true;
  bool _isRunning = false;
  bool _isStarting = false;

  // Form controllers
  final _intervalController = TextEditingController(text: '5');
  final _smtpHostController = TextEditingController();
  final _smtpPortController = TextEditingController(text: '587');
  final _smtpUsernameController = TextEditingController();
  final _smtpPasswordController = TextEditingController();
  final _recipientController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    _intervalController.dispose();
    _smtpHostController.dispose();
    _smtpPortController.dispose();
    _smtpUsernameController.dispose();
    _smtpPasswordController.dispose();
    _recipientController.dispose();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _intervalController.text = prefs.getString('interval') ?? '5';
      _smtpHostController.text = prefs.getString('smtpHost') ?? '';
      _smtpPortController.text = prefs.getString('smtpPort') ?? '587';
      _smtpUsernameController.text = prefs.getString('smtpUsername') ?? '';
      _recipientController.text = prefs.getString('recipient') ?? '';
      // Never load password from storage for security — user must re-enter
    });
    await _checkServiceStatus();
  }

  Future<void> _checkServiceStatus() async {
    final running = await _monitorService.isServiceRunning();
    if (mounted) {
      setState(() {
        _isRunning = running;
        _isLoading = false;
      });
    }
  }

  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('interval', _intervalController.text);
    await prefs.setString('smtpHost', _smtpHostController.text);
    await prefs.setString('smtpPort', _smtpPortController.text);
    await prefs.setString('smtpUsername', _smtpUsernameController.text);
    await prefs.setString('recipient', _recipientController.text);
  }

  Future<void> _startMonitoring() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isStarting = true);

    try {
      await _saveSettings();

      final interval = int.tryParse(_intervalController.text) ?? 5;
      final port = int.tryParse(_smtpPortController.text) ?? 587;

      await _monitorService.requestStartService(
        intervalMinutes: interval,
        smtpHost: _smtpHostController.text.trim(),
        smtpPort: port,
        smtpUsername: _smtpUsernameController.text.trim(),
        smtpPassword: _smtpPasswordController.text,
        recipientEmail: _recipientController.text.trim(),
      );

      if (mounted) {
        setState(() => _isRunning = true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to start: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: Colors.red.shade700,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isStarting = false);
      }
    }
  }

  Future<void> _stopMonitoring() async {
    setState(() => _isStarting = true);
    try {
      await _monitorService.stopService();
      if (mounted) {
        setState(() => _isRunning = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Monitoring stopped'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error stopping: $e'),
            backgroundColor: Colors.red.shade700,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isStarting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Parental Monitor'),
        centerTitle: true,
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          if (_isRunning)
            Container(
              margin: const EdgeInsets.only(right: 12),
              child: Chip(
                avatar: const Icon(Icons.fiber_manual_record, color: Colors.red, size: 14),
                label: const Text('Active', style: TextStyle(fontSize: 12)),
                backgroundColor: Colors.red.shade50,
              ),
            ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: _isRunning ? _buildRunningView() : _buildSetupForm(),
      ),
    );
  }

  Widget _buildRunningView() {
    return Column(
      children: [
        const Icon(Icons.monitor_heart, size: 80, color: Colors.green),
        const SizedBox(height: 16),
        Text(
          'Monitoring Active',
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.bold,
            color: Colors.green.shade700,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Screenshots are being captured every ${_intervalController.text} minutes\nand sent to ${_recipientController.text}',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: Colors.grey.shade600,
          ),
        ),
        const SizedBox(height: 12),
        Card(
          color: Colors.amber.shade50,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Icon(Icons.info_outline, color: Colors.amber.shade800, size: 20),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Close the app or stop below to end monitoring',
                    style: TextStyle(color: Colors.amber.shade900, fontSize: 13),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 32),
        SizedBox(
          width: double.infinity,
          height: 52,
          child: ElevatedButton.icon(
            onPressed: _isStarting ? null : _stopMonitoring,
            icon: _isStarting
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.stop_circle_outlined),
            label: Text(_isStarting ? 'Stopping...' : 'Stop Monitoring'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildSetupForm() {
    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Center(
            child: Column(
              children: [
                Icon(Icons.security, size: 64, color: Theme.of(context).colorScheme.primary),
                const SizedBox(height: 12),
                Text(
                  'Parental Monitor',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Configure monitoring settings below',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.grey.shade600,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),

          // Interval Section
          _sectionHeader('Capture Settings'),
          const SizedBox(height: 8),
          TextFormField(
            controller: _intervalController,
            decoration: const InputDecoration(
              labelText: 'Screenshot interval (minutes)',
              prefixIcon: Icon(Icons.timer),
              border: OutlineInputBorder(),
              helperText: 'How often to capture screenshots (minimum 1)',
            ),
            keyboardType: TextInputType.number,
            validator: (value) {
              if (value == null || value.isEmpty) return 'Required';
              final n = int.tryParse(value);
              if (n == null || n < 1) return 'Minimum 1 minute';
              if (n > 1440) return 'Maximum 1440 minutes (24h)';
              return null;
            },
          ),
          const SizedBox(height: 24),

          // SMTP Section
          _sectionHeader('Email Settings (SMTP)'),
          const SizedBox(height: 4),
          Text(
            'Use an app password for Gmail (not your regular password)',
            style: TextStyle(color: Colors.grey.shade600, fontSize: 12),
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _smtpHostController,
            decoration: const InputDecoration(
              labelText: 'SMTP Host',
              prefixIcon: Icon(Icons.dns),
              border: OutlineInputBorder(),
              hintText: 'smtp.gmail.com',
            ),
            validator: (value) {
              if (value == null || value.trim().isEmpty) return 'Required';
              return null;
            },
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _smtpPortController,
            decoration: const InputDecoration(
              labelText: 'SMTP Port',
              prefixIcon: Icon(Icons.router),
              border: OutlineInputBorder(),
              hintText: '587',
            ),
            keyboardType: TextInputType.number,
            validator: (value) {
              if (value == null || value.isEmpty) return 'Required';
              final n = int.tryParse(value);
              if (n == null || n < 1 || n > 65535) return 'Invalid port (1-65535)';
              return null;
            },
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _smtpUsernameController,
            decoration: const InputDecoration(
              labelText: 'SMTP Username',
              prefixIcon: Icon(Icons.person),
              border: OutlineInputBorder(),
              hintText: 'you@gmail.com',
            ),
            keyboardType: TextInputType.emailAddress,
            validator: (value) {
              if (value == null || value.trim().isEmpty) return 'Required';
              if (!value.contains('@')) return 'Enter a valid email';
              return null;
            },
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _smtpPasswordController,
            decoration: const InputDecoration(
              labelText: 'SMTP Password / App Password',
              prefixIcon: Icon(Icons.lock),
              border: OutlineInputBorder(),
              hintText: 'Your app-specific password',
            ),
            obscureText: true,
            validator: (value) {
              if (value == null || value.isEmpty) return 'Required';
              return null;
            },
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _recipientController,
            decoration: const InputDecoration(
              labelText: 'Recipient Email',
              prefixIcon: Icon(Icons.send),
              border: OutlineInputBorder(),
              hintText: 'parent@example.com',
            ),
            keyboardType: TextInputType.emailAddress,
            validator: (value) {
              if (value == null || value.trim().isEmpty) return 'Required';
              if (!value.contains('@')) return 'Enter a valid email';
              return null;
            },
          ),
          const SizedBox(height: 28),

          // Start Button
          SizedBox(
            width: double.infinity,
            height: 52,
            child: ElevatedButton.icon(
              onPressed: _isStarting ? null : _startMonitoring,
              icon: _isStarting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.play_arrow),
              label: Text(
                _isStarting ? 'Requesting Permission...' : 'Start Monitoring',
                style: const TextStyle(fontSize: 16),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.primary,
                foregroundColor: Theme.of(context).colorScheme.onPrimary,
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Info card
          Card(
            color: Colors.blue.shade50,
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.lightbulb_outline, color: Colors.blue.shade700, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'You will be asked to grant screen capture permission. '
                      'The app will run in the background with a persistent notification.',
                      style: TextStyle(color: Colors.blue.shade900, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _sectionHeader(String title) {
    return Text(
      title,
      style: Theme.of(context).textTheme.titleMedium?.copyWith(
        fontWeight: FontWeight.w600,
      ),
    );
  }
}
