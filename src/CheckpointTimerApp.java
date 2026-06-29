import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;

/** Most of this code was vibe coded using Claude. **/
public class CheckpointTimerApp extends JFrame {
    public CheckpointTimerApp() {
        setTitle("Checkpoint Timer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
        setResizable(true);
        
        add(new TimerPanel());
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CheckpointTimerApp frame = new CheckpointTimerApp();
            frame.setVisible(true);
        });
    }
}

class TimerPanel extends JPanel {
    private List<Integer> checkpointDurations = new ArrayList<>();
    private List<JTextField> durationFields = new ArrayList<>();
    private JButton startButton, resetButton;
    private JPanel checkpointInputPanel;
    private JScrollPane scrollPane;
    
    // Timer state
    private long startSystemTime;
    private long totalCheckpointTimeMs;
    private long elapsedMs;
    private int currentCheckpointIndex;
    private boolean isRunning;
    private boolean timerFinished;
    
    // Timing information
    private LocalTime startClockTime;
    private List<LocalTime> plannedCheckpointTimes = new ArrayList<>();
    private List<LocalTime> actualCheckpointTimes = new ArrayList<>();
    private Set<Integer> completedCheckpoints = new HashSet<>();
    
    public TimerPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(new Color(240, 240, 245));
        
        // Top panel: input controls
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        // Center panel: display area
        JPanel displayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawTimerDisplay((Graphics2D) g);
            }
        };
        displayPanel.setBackground(Color.WHITE);
        add(displayPanel, BorderLayout.CENTER);
        
        // Add initial checkpoint field
        addCheckpointField();
        
        // Update timer: repaint every 50ms for smooth animation
        javax.swing.Timer updateTimer = new javax.swing.Timer(50, e -> {
            if (isRunning) {
                long now = System.currentTimeMillis();
                elapsedMs = now - startSystemTime;
                
                // Check for checkpoint completion
                checkCheckpoints();
                
                // Check if timer is finished
                if (elapsedMs >= totalCheckpointTimeMs && !timerFinished) {
                    timerFinished = true;
                    isRunning = false;
                    startButton.setEnabled(true);
                    playSound(800, 300); // Celebratory beep
                }
            }
            repaint();
        });
        updateTimer.start();
    }
    
    private JPanel createTopPanel() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createTitledBorder("Configure Checkpoints"));
        top.setBackground(new Color(240, 240, 245));
        
        // Checkpoint input area
        checkpointInputPanel = new JPanel();
        checkpointInputPanel.setLayout(new BoxLayout(checkpointInputPanel, BoxLayout.Y_AXIS));
        checkpointInputPanel.setBackground(new Color(240, 240, 245));
        
        scrollPane = new JScrollPane(checkpointInputPanel);
        scrollPane.setPreferredSize(new Dimension(0, 120));
        top.add(scrollPane);
        
        // Add/Remove checkpoint buttons
        JPanel buttonPanel1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel1.setBackground(new Color(240, 240, 245));
        JButton addBtn = new JButton("+ Add Checkpoint");
        JButton removeBtn = new JButton("- Remove Last");
        
        addBtn.addActionListener(e -> addCheckpointField());
        removeBtn.addActionListener(e -> removeLastCheckpoint());
        
        buttonPanel1.add(addBtn);
        buttonPanel1.add(removeBtn);
        top.add(buttonPanel1);
        
        // Start/Reset buttons
        JPanel buttonPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel2.setBackground(new Color(240, 240, 245));
        startButton = new JButton("Start Timer");
        resetButton = new JButton("Reset");
        
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.setBackground(new Color(76, 175, 80));
        startButton.setForeground(Color.WHITE);
        startButton.addActionListener(e -> startTimer());
        
        resetButton.addActionListener(e -> resetTimer());
        
        buttonPanel2.add(startButton);
        buttonPanel2.add(resetButton);
        top.add(buttonPanel2);
        
        return top;
    }
    
    private void addCheckpointField() {
        JPanel checkpointPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));        checkpointPanel.setBackground(Color.WHITE);
        checkpointPanel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        
        JLabel label = new JLabel("Checkpoint " + (durationFields.size() + 1) + ":");
        label.setPreferredSize(new Dimension(100, 25));
        
        JTextField field = new JTextField("30", 8);
        field.setHorizontalAlignment(JTextField.CENTER);
        
        JLabel unitLabel = new JLabel("seconds");
        
        checkpointPanel.add(label);
        checkpointPanel.add(field);
        checkpointPanel.add(unitLabel);
        
        durationFields.add(field);
        checkpointInputPanel.add(checkpointPanel);
        checkpointInputPanel.revalidate();
        checkpointInputPanel.repaint();
    }
    
    private void removeLastCheckpoint() {
        if (!durationFields.isEmpty()) {
            durationFields.remove(durationFields.size() - 1);
            checkpointInputPanel.remove(checkpointInputPanel.getComponentCount() - 1);
            checkpointInputPanel.revalidate();
            checkpointInputPanel.repaint();
        }
    }
    
    private void startTimer() {
        if (isRunning) return;
        
        // Parse durations
        checkpointDurations.clear();
        totalCheckpointTimeMs = 0;
        
        for (JTextField field : durationFields) {
            try {
                int seconds = Integer.parseInt(field.getText().trim());
                if (seconds <= 0) throw new NumberFormatException("Must be positive");
                
                checkpointDurations.add(seconds * 1000);
                totalCheckpointTimeMs += seconds * 1000;
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Invalid input! Each checkpoint must be a positive number.", 
                    "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        if (checkpointDurations.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Add at least one checkpoint!", 
                "No Checkpoints", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Initialize timer
        startSystemTime = System.currentTimeMillis();
        elapsedMs = 0;
        currentCheckpointIndex = 0;
        isRunning = true;
        timerFinished = false;
        completedCheckpoints.clear();
        
        startClockTime = LocalTime.now();
        plannedCheckpointTimes.clear();
        actualCheckpointTimes.clear();
        
        // Calculate planned checkpoint times
        long cumulativeMs = 0;
        for (long duration : checkpointDurations) {
            cumulativeMs += duration;
            plannedCheckpointTimes.add(
                startClockTime.plusNanos(cumulativeMs * 1_000_000L)
            );
        }
        
        startButton.setEnabled(false);
        repaint();
    }
    
    private void checkCheckpoints() {
        for (int i = 0; i < checkpointDurations.size(); i++) {
            if (completedCheckpoints.contains(i)) continue;
            
            long checkpointTimeMs = 0;
            for (int j = 0; j <= i; j++) {
                checkpointTimeMs += checkpointDurations.get(j);
            }
            
            if (elapsedMs >= checkpointTimeMs) {
                completedCheckpoints.add(i);
                actualCheckpointTimes.add(LocalTime.now());
                playSound(1000, 200); // Beep at checkpoint
            }
        }
    }
    
    private void resetTimer() {
        isRunning = false;
        timerFinished = false;
        elapsedMs = 0;
        currentCheckpointIndex = 0;
        totalCheckpointTimeMs = 0;
        completedCheckpoints.clear();
        plannedCheckpointTimes.clear();
        actualCheckpointTimes.clear();
        
        startButton.setEnabled(true);
        repaint();
    }
    
    private void drawTimerDisplay(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();
        
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        
        int x = 20;
        int y = 30;
        int lineHeight = 25;
        
        // If not started yet
        if (!isRunning && totalCheckpointTimeMs == 0) {
            g.setFont(new Font("Arial", Font.ITALIC, 16));
            g.drawString("Configure checkpoints above and click 'Start Timer'", x, y);
            return;
        }
        
        // Start time
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Start Time: " + 
            (startClockTime != null ? 
                startClockTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) 
                : "—"), x, y);
        y += lineHeight;
        
        // Overall progress
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        long remainingMs = Math.max(0, totalCheckpointTimeMs - elapsedMs);
        g.drawString("Total Elapsed: " + formatTime(elapsedMs) + 
                     "  |  Total Remaining: " + formatTime(remainingMs) + 
                     "  |  Total Duration: " + formatTime(totalCheckpointTimeMs), x, y);
        y += lineHeight;
        
        // Overall progress bar
        y += 5;
        drawProgressBar(g, x, y, width - 40, 25, (float) elapsedMs / totalCheckpointTimeMs);
        y += 35;
        
        // Current checkpoint info
        if (!isRunning && timerFinished) {
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.setColor(new Color(76, 175, 80));
            g.drawString("✓ Timer Complete!", x, y);
            g.setColor(Color.BLACK);
        } else if (currentCheckpointIndex < checkpointDurations.size()) {
            long checkpointStartMs = 0;
            for (int i = 0; i < currentCheckpointIndex; i++) {
                checkpointStartMs += checkpointDurations.get(i);
            }
            
            long timeInCheckpointMs = elapsedMs - checkpointStartMs;
            long checkpointDurationMs = checkpointDurations.get(currentCheckpointIndex);
            float progress = Math.min(1.0f, (float) timeInCheckpointMs / checkpointDurationMs);
            
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Current Checkpoint " + (currentCheckpointIndex + 1) + " Progress:", x, y);
            y += lineHeight;
            
            drawProgressBar(g, x, y, width - 40, 20, progress);
            y += 30;
            
            g.setFont(new Font("Arial", Font.PLAIN, 13));
            g.drawString("Time: " + formatTime(timeInCheckpointMs) + " / " + 
                        formatTime(checkpointDurationMs), x, y);
        }
        
        y += lineHeight + 10;
        
        // Checkpoints list
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Checkpoints:", x, y);
        y += lineHeight;
        
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        
        for (int i = 0; i < checkpointDurations.size(); i++) {
            // Status indicator
            String status;
            Color statusColor = Color.BLACK;
            if (completedCheckpoints.contains(i)) {
                status = "✓";
                statusColor = new Color(76, 175, 80); // Green
            } else if (i == currentCheckpointIndex && isRunning) {
                status = "→";
                statusColor = new Color(33, 150, 243); // Blue
            } else {
                status = "○";
                statusColor = new Color(150, 150, 150); // Gray
            }
            
            g.setColor(statusColor);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString(status, x + 5, y);
            
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 11));
            
            StringBuilder info = new StringBuilder();
            info.append("  Checkpoint ").append(i + 1)
                .append(": ").append(formatTime((long) checkpointDurations.get(i)));
            
            if (i < plannedCheckpointTimes.size()) {
                info.append("  |  Planned: ")
                    .append(plannedCheckpointTimes.get(i)
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
            
            if (i < actualCheckpointTimes.size()) {
                info.append("  |  Actual: ")
                    .append(actualCheckpointTimes.get(i)
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
            
            g.drawString(info.toString(), x + 20, y);
            y += lineHeight;
        }
    }
    
    private void drawProgressBar(Graphics2D g, int x, int y, int width, int height, float progress) {
        // Background
        g.setColor(new Color(200, 200, 200));
        g.fillRect(x, y, width, height);
        
        // Progress fill
        g.setColor(new Color(33, 150, 243));
        int fillWidth = (int) (width * Math.min(1.0f, progress));
        g.fillRect(x, y, fillWidth, height);
        
        // Border
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRect(x, y, width, height);
        
        // Percentage text
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 11));
        String percentText = String.format("%.0f%%", progress * 100);
        FontMetrics fm = g.getFontMetrics();
        int textX = x + (width - fm.stringWidth(percentText)) / 2;
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(percentText, textX, textY);
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%d s", seconds);
        }
    }

    private void playSound(int frequency, int durationMs) {
        try {
            // Audio format: 16-bit, 44100 Hz sample rate, mono
            int sampleRate = 44100;
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);

            // Calculate number of samples needed
            int numSamples = (sampleRate * durationMs) / 1000;
            byte[] audioData = new byte[numSamples * 2]; // 16-bit = 2 bytes per sample

            // Generate sine wave
            for (int i = 0; i < numSamples; i++) {
                // Create sine wave at the specified frequency
                double sampleValue = Math.sin(2.0 * Math.PI * i * frequency / sampleRate);

                // Apply envelope (fade in and fade out to avoid clicking)
                double envelope = 1.0;
                int fadeLength = sampleRate / 20; // Fade over 50ms
                if (i < fadeLength) {
                    envelope = (double) i / fadeLength;
                } else if (i > numSamples - fadeLength) {
                    envelope = (double) (numSamples - i) / fadeLength;
                }

                // Reduce volume to 50% to prevent clipping
                sampleValue *= envelope * 0.5;

                // Convert to 16-bit PCM format
                short sampleShort = (short) (sampleValue * 32767);
                audioData[i * 2] = (byte) (sampleShort & 0xFF);
                audioData[i * 2 + 1] = (byte) ((sampleShort >> 8) & 0xFF);
            }

            // Play the sound
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            line.write(audioData, 0, audioData.length);
            line.drain();
            line.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
