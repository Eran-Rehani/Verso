# Mathematical Analysis & Physics Primer

Welcome to **Verso**. Use the **Table of Contents** button in the top bar (or the bottom chapter bar) to jump between chapters, tap **Search** to find keywords, or tap the **Bookmark** icon to save pages to the sidebar. All inline and display $\LaTeX$ equations are typeset natively.

## 1. Algebraic Foundations

For any quadratic polynomial $ax^2 + bx + c = 0$ with $a \neq 0$ and coefficients in $\mathbb{R}$, the roots are given by the classical quadratic formula:

$$x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \tag{1.1}$$

### 1.1 Binomial Theorem & Series

For any non-negative integer $n \in \mathbb{N}$, the binomial expansion of $(x + y)^n$ is expressed using the binomial coefficient $\binom{n}{k}$:

$$(x + y)^n = \sum_{k=0}^{n} \binom{n}{k} x^{n-k} y^k = \sum_{k=0}^{n} \frac{n!}{k!(n-k)!} x^{n-k} y^k \tag{1.2}$$

## 2. Calculus & Gaussian Integrals

One of the most celebrated identities in analysis is the Euler–Poisson Gaussian integral over the real line $\mathbb{R}$:

$$\int_{-\infty}^{\infty} e^{-x^2} \, dx = \sqrt{\pi} \tag{2.1}$$

### 2.1 Fundamental Limits & Derivatives

The derivative of a smooth function $f(x)$ at a point $x_0$ is defined by the limit:

$$f'(x_0) = \lim_{h \to 0} \frac{f(x_0 + h) - f(x_0)}{h}$$

And the Riemann zeta function $\zeta(s)$ for $\Re(s) > 1$ is given by both an infinite series and an Euler product over primes $p$:

$$\zeta(s) = \sum_{n=1}^{\infty} \frac{1}{n^s} = \prod_{p} \left( \frac{1}{1 - p^{-s}} \right) \tag{2.2}$$

## 3. Linear Algebra & Matrices

Consider a rotation matrix $R(\theta) \in \mathbb{R}^{2 \times 2}$ and the identity matrix $I_2$:

$$R(\theta) = \begin{pmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{pmatrix}, \quad \det R(\theta) = \cos^2\theta + \sin^2\theta = 1 \tag{3.1}$$

### 3.1 Piecewise Functions & Cases

The absolute value $|x|$ and the ReLU activation function $\sigma(x)$ can be written using piecewise cases:

$$|x| = \begin{cases} x & \text{if } x \ge 0 \\ -x & \text{if } x < 0 \end{cases}$$

## 4. Quantum Mechanics & Electrodynamics

The time-dependent Schrödinger wave equation governs the evolution of the state vector $\psi(\vec{r}, t)$ under the Hamiltonian operator $\hat{H}$:

$$i\hbar \frac{\partial \psi}{\partial t} = \left( -\frac{\hbar^2}{2m} \nabla^2 + V(\vec{r}, t) \right) \psi(\vec{r}, t) \tag{4.1}$$

### 4.1 Maxwell's Equations in Differential Form

In vacuum, the electric field $\vec{E}$ and magnetic field $\vec{B}$ satisfy the coupled system:

$$\begin{aligned} \nabla \cdot \vec{E} &= \frac{\rho}{\varepsilon_0} \\ \nabla \cdot \vec{B} &= 0 \\ \nabla \times \vec{E} &= -\frac{\partial \vec{B}}{\partial t} \\ \nabla \times \vec{B} &= \mu_0 \vec{J} + \mu_0 \varepsilon_0 \frac{\partial \vec{E}}{\partial t} \end{aligned} \tag{4.2}$$
