from setuptools import setup, find_packages

setup(
    name='quantiq-ml-trainer',
    version='1.0',
    packages=find_packages(),
    install_requires=[
        'pandas>=1.5.0',
        'numpy>=1.23.0',
        'scikit-learn>=1.2.0',
        'google-cloud-aiplatform>=1.25.0',
        'google-cloud-storage>=2.9.0',
    ],
)
