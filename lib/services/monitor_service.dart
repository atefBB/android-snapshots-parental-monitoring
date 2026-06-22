import 'package:flutter/services.dart';

/// Bridge to the native Android screenshot service via platform channels.
class MonitorService {
  static const _channel = MethodChannel('com.parentalmonitor/screenshot');

  /// Check if the screenshot service is currently running.
  Future<bool> isServiceRunning() async {
    try {
      final result = await _channel.invokeMethod<bool>('isServiceRunning');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  /// Request to start the screenshot service.
  ///
  /// This will trigger the Android MediaProjection permission dialog.
  /// [intervalMinutes] - how often to take screenshots (minimum 1).
  /// [smtpHost] - SMTP server hostname.
  /// [smtpPort] - SMTP server port.
  /// [smtpUsername] - SMTP username (email address).
  /// [smtpPassword] - SMTP password or app password.
  /// [recipientEmail] - email address to send screenshots to.
  /// [fromEmail] - optional sender email (defaults to smtpUsername).
  Future<bool> requestStartService({
    required int intervalMinutes,
    required String smtpHost,
    required int smtpPort,
    required String smtpUsername,
    required String smtpPassword,
    required String recipientEmail,
    String? fromEmail,
  }) async {
    try {
      final result = await _channel.invokeMethod<bool>('requestStartService', {
        'intervalMinutes': intervalMinutes,
        'smtpHost': smtpHost,
        'smtpPort': smtpPort,
        'smtpUsername': smtpUsername,
        'smtpPassword': smtpPassword,
        'recipientEmail': recipientEmail,
        'fromEmail': fromEmail ?? smtpUsername,
      });
      return result ?? false;
    } on PlatformException catch (e) {
      throw Exception(e.message ?? 'Failed to start service');
    }
  }

  /// Stop the screenshot service.
  Future<bool> stopService() async {
    try {
      final result = await _channel.invokeMethod<bool>('stopService');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }
}
