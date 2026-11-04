#include "audio_analysis.h"
#include <cmath>
#include <cstdio>
#include <random>
#include <string>
#include <vector>
using namespace bitchord::smart;

static const double SR = 22050.0;
static const double PI = 3.14159265358979323846;

// scale degrees of major / natural minor, and I-IV-V-I style chord roots (in semitones above tonic)
static void addNote(std::vector<float>& x, double t0, double dur, double midi, double amp, double cents) {
  double f0 = 440.0 * std::pow(2.0, (midi - 69.0 + cents / 100.0) / 12.0);
  size_t a = (size_t)(t0 * SR), b = std::min(x.size(), (size_t)((t0 + dur) * SR));
  for (size_t i = a; i < b; ++i) {
    double t = (double)(i - a) / SR;
    double env = std::min(1.0, t * 40) * std::exp(-t * 1.2);
    double v = 0;
    for (int h = 1; h <= 5; ++h) v += std::sin(2 * PI * f0 * h * t) / h;
    x[i] += (float)(amp * env * v);
  }
}

// Render `seconds` of music in a given key (tonic pitch class 0..11, minor?) with
// a 120 BPM kick, a bass line an octave+ below and tuned `cents` off A440.
static void render(std::vector<float>& x, double from, double to, int tonic, bool minor, double cents, std::mt19937& rng) {
  const int major_deg[7] = {0, 2, 4, 5, 7, 9, 11};
  const int minor_deg[7] = {0, 2, 3, 5, 7, 8, 10};
  const int* deg = minor ? minor_deg : major_deg;
  const int prog[4] = {0, 3, 4, 0};  // I IV V I (by scale degree index)
  double beat = 0.5;
  int bar = 0;
  for (double t = from; t < to; t += 4 * beat, ++bar) {
    int d = prog[bar % 4];
    int root = tonic + deg[d];
    int third = tonic + deg[(d + 2) % 7] + (d + 2 >= 7 ? 12 : 0);
    int fifth = tonic + deg[(d + 4) % 7] + (d + 4 >= 7 ? 12 : 0);
    for (int n : {root, third, fifth}) addNote(x, t, 4 * beat, 60 + n % 12 + (n >= 12 ? 12 : 0) , 0.10, cents);
    addNote(x, t, beat * 2, 36 + root % 12, 0.35, cents);   // bass note, below the chroma floor
    // a melody wandering in the scale
    for (int k = 0; k < 4; ++k) addNote(x, t + k * beat, beat, 72 + deg[rng() % 7], 0.06, cents);
    for (int k = 0; k < 4; ++k) {                              // kick
      size_t a = (size_t)((t + k * beat) * SR);
      for (size_t i = 0; i < (size_t)(0.15 * SR) && a + i < x.size(); ++i) {
        double tt = (double)i / SR;
        x[a + i] += (float)(0.6 * std::sin(2 * PI * (55 + 120 * std::exp(-tt * 40)) * tt) * std::exp(-tt * 18));
      }
    }
  }
}

static const char* NAMES[12] = {"C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B"};

static int run(const char* label, int tonic, bool minor, double cents) {
  std::mt19937 rng(7);
  std::vector<float> x((size_t)(70 * SR), 0.f);
  render(x, 0, 70, tonic, minor, cents, rng);
  AnalysisResult r = AnalyzeAudio(x, SR, 70.0);
  std::string want = std::string(NAMES[tonic]) + (minor ? " minor" : " major");
  printf("%-28s want %-9s got key=%-12s conf=%.2f  head=%-12s %.2f  tail=%-12s %.2f  tuning=%+.0fc\n",
         label, want.c_str(), r.key.c_str(), r.key_confidence,
         r.key_start.c_str(), r.key_start_confidence, r.key_end.c_str(), r.key_end_confidence, r.tuning_cents);
  return 0;
}

int main() {
  run("A minor, in tune", 9, true, 0);
  run("C major, in tune", 0, false, 0);
  run("Eb major, in tune", 3, false, 0);
  run("F# minor, in tune", 6, true, 0);
  run("D major, -35 cents flat", 2, false, -35);
  run("G minor, +30 cents sharp", 7, true, 30);
  run("Bb major, -45 cents", 10, false, -45);

  // modulating track: first 35 s in C major, last 35 s in E major (up a major third)
  std::mt19937 rng(3);
  std::vector<float> x((size_t)(70 * SR), 0.f);
  render(x, 0, 35, 0, false, 0, rng);
  render(x, 35, 70, 4, false, 0, rng);
  AnalysisResult r = AnalyzeAudio(x, SR, 70.0);
  printf("%-28s key=%-10s head=%-10s %.2f tail=%-10s %.2f\n", "C major -> E major",
         r.key.c_str(), r.key_start.c_str(), r.key_start_confidence, r.key_end.c_str(), r.key_end_confidence);
  return 0;
}
