#!/bin/bash

# Script to install git hooks for the project

echo "Installing git hooks..."

# Create hooks directory if it doesn't exist
mkdir -p .git/hooks

# Create pre-commit hook
cat > .git/hooks/pre-commit << 'EOF'
#!/bin/bash

# Run Spotless formatting check
echo "Running code formatting check..."
./gradlew spotlessCheck

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Code formatting check failed!"
    echo "Run './gradlew spotlessApply' to fix formatting issues."
    echo ""
    exit 1
fi

echo "✅ Code formatting check passed!"
EOF

# Make the hook executable
chmod +x .git/hooks/pre-commit

echo "✅ Git hooks installed successfully!"
echo ""
echo "The pre-commit hook will:"
echo "  - Check code formatting with Spotless"
echo ""
echo "To skip the hook temporarily, use: git commit --no-verify"
echo "To fix formatting issues, run: ./gradlew spotlessApply"