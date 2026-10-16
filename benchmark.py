import matplotlib
matplotlib.use('Agg')  # Headless mode for Windows/Tkinter compatibility
import matplotlib.pyplot as plt
import pandas as pd

def plot_performance():
    # Sample data for loss rates (0% to 30%) and goodput
    loss_rates = [0, 5, 10, 15, 20, 25, 30]
    goodput_stop_wait = [1200, 900, 500, 200, 100, 50, 20]
    goodput_go_back_n = [1480, 1200, 750, 400, 180, 80, 30]
    goodput_selective_repeat = [1500, 1450, 1400, 1300, 1100, 900, 750]

    plt.figure(figsize=(8, 5))
    plt.plot(loss_rates, goodput_stop_wait, marker='o', label='Stop-and-Wait')
    plt.plot(loss_rates, goodput_go_back_n, marker='^', label='Go-Back-N')
    plt.plot(loss_rates, goodput_selective_repeat, marker='s', label='Selective Repeat')
    
    plt.xlabel('Packet Loss Rate (%)')
    plt.ylabel('Goodput (Bytes/sec)')
    plt.title('ARQ Protocols Performance Comparison (Goodput vs Loss Rate)')
    plt.legend()
    plt.grid(True)
    
    plt.savefig('performance_curve.png')
    print("Benchmark plot generated and saved successfully as 'performance_curve.png'!")

if __name__ == "__main__":
    plot_performance()