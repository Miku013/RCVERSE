document.addEventListener('DOMContentLoaded', () => {
  // モバイルメニュー開閉
  const menuToggle = document.querySelector('.menu-toggle');
  const navLinks = document.querySelector('.nav-links');

  if (menuToggle && navLinks) {
    menuToggle.addEventListener('click', () => {
      navLinks.classList.toggle('active');
    });

    // リンククリック時にメニューを閉じる
    navLinks.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('active');
      });
    });
  }

  // スクロール時にナビゲーションバーのスタイル変更
  const navbar = document.querySelector('.navbar');
  window.addEventListener('scroll', () => {
    if (window.scrollY > 50) {
      navbar.style.borderBottomColor = 'rgba(56, 189, 248, 0.4)';
      navbar.style.boxShadow = '0 4px 20px rgba(0, 0, 0, 0.5)';
    } else {
      navbar.style.borderBottomColor = 'rgba(56, 189, 248, 0.2)';
      navbar.style.boxShadow = 'none';
    }
  });
});
